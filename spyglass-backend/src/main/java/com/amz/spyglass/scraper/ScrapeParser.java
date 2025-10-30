package com.amz.spyglass.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 页面解析器（中文注释）
 * 将 Jsoup 的 Document 解析成我们需要的字段（title、price、bsr、inventory、imageMd5、aplusMd5）。
 * 注意：解析规则使用启发式选择器，亚马逊页面多变，生产环境需根据实际页面调整或使用更稳健的策略。
 */
public class ScrapeParser {
    
    private static final Logger logger = LoggerFactory.getLogger(ScrapeParser.class);

    /**
     * 解析 Document 并返回填充的 AsinSnapshot（不包含 snapshotAt，调用方可设置）
     */
    public static AsinSnapshotDTO parse(Document doc) {
        AsinSnapshotDTO s = new AsinSnapshotDTO();
        if (doc == null) return s;

        // title
        String title = doc.title();
        s.setTitle(title);

        // price: 尝试多个常见选择器（扩展版本）
        String priceText = null;
        String[] priceSelectors = {
            "#priceblock_ourprice",
            "#priceblock_dealprice",
            ".a-price .a-offscreen",
            "#tp_price_block_total_price_ww",
            "#corePrice_feature_div .a-price .a-offscreen",
            ".a-price[data-a-color=price] .a-offscreen",
            "#price_inside_buybox",
            "#newBuyBoxPrice",
            "[data-a-color=price] .a-offscreen"
        };
        
        for (String selector : priceSelectors) {
            Element pEl = doc.selectFirst(selector);
            if (pEl != null) {
                priceText = pEl.text();
                if (priceText != null && !priceText.trim().isEmpty()) {
                    logger.debug("价格选择器命中: {} -> {}", selector, priceText);
                    break;
                }
            }
        }
        
        BigDecimal price = parsePriceToBigDecimal(priceText);
        s.setPrice(price);
        if (price == null) {
            logger.debug("价格解析失败，所有选择器均未命中");
        }

        // BSR: 在详情或者排名区域查找 "Best Sellers Rank" 或者 "Best Sellers"（扩展版本）
        Integer bsr = null;
        
        // 尝试多个选择器区域
        String[] bsrContainerSelectors = {
            "#detailBullets_feature_div li",
            "#productDetails_detailBullets_sections1 tr",
            "#detailBulletsWrapper_feature_div li",
            "#productDetails_techSpec_section_1 tr",
            ".product-facts-detail"
        };
        
        for (String containerSelector : bsrContainerSelectors) {
            Elements elements = doc.select(containerSelector);
            for (Element e : elements) {
                String txt = e.text().toLowerCase(Locale.ROOT);
                if (txt.contains("best sellers rank") || txt.contains("best seller rank") || txt.contains("best sellers")) {
                    String digits = txt.replaceAll("[^0-9,]", "");
                    digits = digits.replaceAll(",", "");
                    try {
                        if (!digits.isEmpty()) {
                            bsr = Integer.parseInt(digits);
                            logger.debug("BSR选择器命中: {} -> {}", containerSelector, bsr);
                        }
                    } catch (NumberFormatException ex) {
                        // ignore
                    }
                    break;
                }
            }
            if (bsr != null) break;
        }
        
        // 备用选择器：查找 "#SalesRank"
        if (bsr == null) {
            Element sr = doc.selectFirst("#SalesRank");
            if (sr != null) {
                String txt = sr.text().replaceAll("[^0-9,]", "").replaceAll(",", "");
                try { 
                    if (!txt.isEmpty()) {
                        bsr = Integer.parseInt(txt);
                        logger.debug("BSR备用选择器命中: #SalesRank -> {}", bsr);
                    }
                } catch (NumberFormatException ex) {}
            }
        }
        
        s.setBsr(bsr);
        if (bsr == null) {
            logger.debug("BSR解析失败，所有选择器均未命中");
        }

        // inventory: 简单查找 "In Stock" 或数字形式
        Integer inventory = null;
        Element invNode = doc.selectFirst("#availability, #availability_feature_div");
        if (invNode != null) {
            String invText = invNode.text().toLowerCase(Locale.ROOT);
            if (invText.contains("in stock")) {
                inventory = null; // 无精确数值，表示有库存
            } else {
                String digits = invText.replaceAll("[^0-9]", "");
                try { if (!digits.isEmpty()) inventory = Integer.parseInt(digits); } catch (NumberFormatException ex) {}
            }
        }
    s.setInventory(inventory);

        // image md5: 获取主图 URL，然后对 URL 字符串做 MD5（注意：这不是图片内容的 MD5，仅作为占位）
        String imgUrl = null;
        Element img = doc.selectFirst("#landingImage, #imgTagWrapperId img, img#main-image, img#image-block img");
        if (img != null) {
            imgUrl = img.attr("src");
            if (imgUrl == null || imgUrl.isEmpty()) imgUrl = img.attr("data-old-hires");
        }
    s.setImageMd5(imgUrl == null ? null : md5Hex(imgUrl));

        // feature bullets（五点要点）: 优先选择常见的 #feature-bullets 列表
        try {
            Elements bullets = doc.select("#feature-bullets .a-list-item, #feature-bullets li");
            StringBuilder sb = new StringBuilder();
            for (Element b : bullets) {
                String text = b.text().trim();
                if (text.isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(text);
            }
            String bulletsText = sb.length() == 0 ? null : sb.toString();
            s.setBulletPoints(bulletsText);
        } catch (Exception ignored) {
            s.setBulletPoints(null);
        }

        // A+ 内容 MD5：尝试根据 aplus 区域获取 HTML 并计算 MD5（同样仅基于 HTML 字符串）
        Element aplus = doc.selectFirst("#aplus, .aplus, .a-plus");
        String aplusHtml = aplus == null ? null : aplus.html();
        s.setAplusMd5(aplusHtml == null ? null : md5Hex(aplusHtml));

        // 评论总数与平均评分（扩展选择器版本）
        try {
            String reviewsText = null;
            String[] reviewCountSelectors = {
                "#acrCustomerReviewText",
                "#reviewSummary .a-section .a-size-base",
                "[data-hook=total-review-count]",
                ".averageStarRatingNumerical",
                "#acrPopover + .a-declarative .a-size-base"
            };
            
            for (String selector : reviewCountSelectors) {
                Element revEl = doc.selectFirst(selector);
                if (revEl != null) {
                    reviewsText = revEl.text();
                    if (reviewsText != null && !reviewsText.trim().isEmpty()) {
                        logger.debug("评论总数选择器命中: {} -> {}", selector, reviewsText);
                        break;
                    }
                }
            }
            
            if (reviewsText != null) {
                String digits = reviewsText.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    s.setTotalReviews(Integer.parseInt(digits));
                }
            } else {
                logger.debug("评论总数解析失败，所有选择器均未命中");
            }

            // 平均评分扩展选择器
            String[] ratingSelectors = {
                "#averageCustomerReviews .a-icon-alt",
                "span[data-hook=rating-out-of-text]",
                ".review-rating",
                "#acrPopover",
                ".a-icon-star .a-icon-alt"
            };
            
            for (String selector : ratingSelectors) {
                Element avgEl = doc.selectFirst(selector);
                if (avgEl != null) {
                    String avgText = avgEl.text().replaceAll("[^0-9\\.]", "");
                    if (!avgText.isEmpty()) {
                        s.setAvgRating(new java.math.BigDecimal(avgText));
                        logger.debug("平均评分选择器命中: {} -> {}", selector, avgText);
                        break;
                    }
                }
            }
            
            if (s.getAvgRating() == null) {
                logger.debug("平均评分解析失败，所有选择器均未命中");
            }
        } catch (Exception e) {
            logger.debug("评论/评分解析异常: {}", e.getMessage());
        }

        // 尝试抓取最近的差评（1-3星），选择评论列表中第一个满足条件的项
        try {
            org.jsoup.select.Elements reviewEls = doc.select(".review, .a-section.review");
            for (org.jsoup.nodes.Element re : reviewEls) {
                String ratingText = null;
                org.jsoup.nodes.Element r = re.selectFirst(".a-icon-alt, .review-rating");
                if (r != null) ratingText = r.text();
                if (ratingText != null) {
                    String digit = ratingText.replaceAll("[^0-9]", "");
                    if (!digit.isEmpty()) {
                        int rating = Integer.parseInt(digit.substring(0, Math.min(digit.length(), 1)));
                        if (rating >=1 && rating <=3) {
                            // 找到差评，计算其 MD5（基于评论内容+时间）
                            org.jsoup.nodes.Element content = re.selectFirst(".review-text, .a-size-base.review-text");
                            org.jsoup.nodes.Element dateEl = re.selectFirst(".review-date");
                            if (content != null && dateEl != null) {
                                // 组合评论内容和时间计算 MD5，避免因相同内容产生误报
                                String reviewText = content.text();
                                String reviewDate = dateEl.text();
                                s.setLatestNegativeReviewMd5(md5Hex(reviewText + "|" + reviewDate));
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return s;
    }

    private static BigDecimal parsePriceToBigDecimal(String priceText) {
        if (priceText == null) return null;
        // 去掉货币符号与逗号
        String cleaned = priceText.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private static String md5Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
