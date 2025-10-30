package com.amz.spyglass.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 页面解析器（中文注释）
 * 将 Jsoup 的 Document 解析成我们需要的字段（title、price、bsr、inventory、imageMd5、aplusMd5）。
 * 注意：解析规则使用启发式选择器，亚马逊页面多变，生产环境需根据实际页面调整或使用更稳健的策略。
 */
public class ScrapeParser {

    private static final Logger log = LoggerFactory.getLogger(ScrapeParser.class);

    /**
     * 解析 Document 并返回填充的 AsinSnapshot（不包含 snapshotAt，调用方可设置）
     */
    public static AsinSnapshotDTO parse(Document doc) {
        AsinSnapshotDTO s = new AsinSnapshotDTO();
        if (doc == null) return s;

        // title
        String title = doc.title();
        s.setTitle(title);

        // price: 尝试多个常见选择器 + 备用结构
        String priceText = null;
        Element p1 = doc.selectFirst("span.a-price[data-a-color=price] span.a-offscreen, span.a-price.a-text-price span.a-offscreen");
        if (p1 != null) priceText = p1.text();
        if (priceText == null) {
            Element p2 = doc.selectFirst("#priceblock_ourprice, #priceblock_dealprice, #tp_price_block_total_price_ww, #corePriceDisplay_desktop_feature_div span.a-offscreen");
            if (p2 != null) priceText = p2.text();
        }
        if (priceText == null) {
            Element p3 = doc.selectFirst("div#corePrice_feature_div span.a-offscreen");
            if (p3 != null) priceText = p3.text();
        }
        // 命中统计
        log.debug("[Parse] price selectors hit={}", (priceText != null));
        s.setPrice(parsePriceToBigDecimal(priceText));

        // BSR: 扩展更多区域，增加 detailBulletsWrapper
        Integer bsr = null;
        Elements detailBullets = doc.select("#detailBullets_feature_div li, #detailBulletsWrapper_feature_div li, #productDetails_detailBullets_sections1 tr, div.a-section.a-spacing-small.add-to-cart-wrapper + div ul li");
        for (Element e : detailBullets) {
            String txt = e.text().toLowerCase(Locale.ROOT);
            if (txt.contains("best sellers rank") || txt.contains("best seller rank") || txt.contains("best sellers")) {
                // 优化数字提取逻辑，优先提取 # 开头的数字，并只取第一个
                String rankCandidate = txt;
                if (rankCandidate.contains("#")) {
                    rankCandidate = rankCandidate.substring(rankCandidate.indexOf("#") + 1);
                    // 去掉数字中的逗号，并只取数字部分
                    Pattern pattern = Pattern.compile("^[0-9,]+");
                    Matcher matcher = pattern.matcher(rankCandidate);
                    if (matcher.find()) {
                        rankCandidate = matcher.group(0);
                    }
                }
                String digits = rankCandidate.replaceAll("[^0-9]", "");
                try { if (!digits.isEmpty()) { bsr = Integer.parseInt(digits); break; } } catch (NumberFormatException ex) {}
            }
        }
        if (bsr == null) {
            Element sr = doc.selectFirst("#SalesRank, span#SalesRank");
            if (sr != null) {
                String digits = sr.text().replaceAll("[^0-9]", "");
                try { if (!digits.isEmpty()) bsr = Integer.parseInt(digits); } catch (NumberFormatException ex) {}
            }
        }
        log.debug("[Parse] bsr hit={}", (bsr != null));
        s.setBsr(bsr);

        // inventory: 增加 buyBox 区域与备选 class
        Integer inventory = null;
        Element invNode = doc.selectFirst("#availability, #availability_feature_div, #desktop_qualifiedBuyBox_feature_div, div#availability span, div#availability_feature_div span");
        if (invNode != null) {
            String invText = invNode.text().toLowerCase(Locale.ROOT);
            if (invText.contains("only") && invText.contains("left in stock")) {
                String digits = invText.replaceAll("[^0-9]", "");
                try { if (!digits.isEmpty()) inventory = Integer.parseInt(digits); } catch (NumberFormatException ex) {}
            } else if (invText.contains("in stock")) {
                String digits = invText.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try { inventory = Integer.parseInt(digits); } catch (NumberFormatException ex) { inventory = null; }
                } else {
                    inventory = null; // 有货未知数量
                }
            }
        }
        log.debug("[Parse] inventory hit={}", (inventory != null));
        s.setInventory(inventory);

        // image md5: 获取主图 URL，然后对 URL 字符串做 MD5（注意：这不是图片内容的 MD5，仅作为占位）
        String imgUrl = null;
        Element img = doc.selectFirst("#landingImage, #imgTagWrapperId img, img#main-image, img#image-block img");
        if (img != null) {
            imgUrl = img.attr("src");
            if (imgUrl == null || imgUrl.isEmpty()) {
                String fallback = img.attr("data-old-hires");
                if (fallback != null && !fallback.isEmpty()) imgUrl = fallback;
            }
        }
        s.setImageMd5(imgUrl == null ? null : md5Hex(imgUrl));

        // feature bullets（五点要点）: 优先选择常见的 #feature-bullets 列表
        try {
            Elements bullets = doc.select("#feature-bullets .a-list-item, #feature-bullets li");
            StringBuilder sb = new StringBuilder();
            for (Element b : bullets) {
                String text = b.text().trim();
                if (text.isEmpty()) continue;
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(text);
            }
            String bulletsText = sb.isEmpty() ? null : sb.toString();
            s.setBulletPoints(bulletsText);
        } catch (Exception ignored) {
            s.setBulletPoints(null);
        }

        // A+ 内容 MD5：尝试根据 aplus 区域获取 HTML 并计算 MD5（同样仅基于 HTML 字符串）
        Element aplus = doc.selectFirst("#aplus, .aplus, .a-plus");
        String aplusHtml = aplus == null ? null : aplus.html();
        s.setAplusMd5(aplusHtml == null ? null : md5Hex(aplusHtml));

        // 评论总数与平均评分（常见选择器 + 备用）
        try {
            String reviewsText = null;
            org.jsoup.nodes.Element revEl = doc.selectFirst("#acrCustomerReviewText, span[data-hook=total-review-count], #averageCustomerReviews span.a-size-base");
            if (revEl != null) reviewsText = revEl.text();
            if (reviewsText != null) {
                String digits = reviewsText.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) s.setTotalReviews(Integer.parseInt(digits));
            }

            // 平均评分选择器
            org.jsoup.nodes.Element avgEl = doc.selectFirst("span[data-hook=rating-out-of-text], i[data-hook=average-star-rating] span.a-icon-alt, #averageCustomerReviews span.a-icon-alt");
            if (avgEl != null) {
                String avgText = avgEl.text().split("out")[0].replaceAll("[^0-9.]", "").trim();
                if (!avgText.isEmpty()) s.setAvgRating(new java.math.BigDecimal(avgText));
            }
        } catch (Exception ignored) {}
        log.debug("[Parse] reviews hit={} , rating hit={}", (s.getTotalReviews() != null), (s.getAvgRating() != null));

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
                        int rating = Integer.parseInt(digit.substring(0, 1));
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
