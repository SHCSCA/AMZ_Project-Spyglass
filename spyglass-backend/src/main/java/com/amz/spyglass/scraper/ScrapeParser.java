package com.amz.spyglass.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * 亚马逊商品页面解析器
 * 
 * 负责从亚马逊商品详情页面HTML中提取关键商品信息，包括：
 * - 商品标题、价格、库存状态
 * - BSR排名信息（主排名、大类、小类及小类排名）
 * - 评价信息（平均评分、总评论数）
 * - 内容MD5哈希（主图、A+内容、最新差评）
 * - 商品特征要点
 * 
 * 支持多种亚马逊页面格式，具备容错和回退机制
 * 注意：解析规则使用启发式选择器，亚马逊页面结构可能变化，生产环境需要监控和调整
 * 
 * @author AI Assistant
 * @version 2.0.0
 * @since 2025-10-31
 */
public class ScrapeParser {


    /**
     * 解析 HTML 字符串并返回填充的 AsinSnapshot（不包含 snapshotAt，调用方可设置）
     */
    public static AsinSnapshotDTO parse(String html, String url) {
        if (html == null || html.isEmpty()) {
            return new AsinSnapshotDTO();
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, url);
        return parse(doc);
    }

    /**
     * 解析 Document 并返回填充的 AsinSnapshot（不包含 snapshotAt，调用方可设置）
     */
    public static AsinSnapshotDTO parse(Document doc) {
        AsinSnapshotDTO s = new AsinSnapshotDTO();
        if (doc == null) return s;

        // title
        String title = doc.title();
        s.setTitle(title);

        // price: 尝试多个常见选择器
        String priceText = null;
        Element p1 = doc.selectFirst("#priceblock_ourprice, #priceblock_dealprice, .a-price .a-offscreen");
        if (p1 != null) priceText = p1.text();
        if (priceText == null) {
            Element p2 = doc.selectFirst("#tp_price_block_total_price_ww");
            if (p2 != null) priceText = p2.text();
        }
        s.setPrice(parsePriceToBigDecimal(priceText));

        // BSR: 查找 "Best Sellers Rank" 信息，支持多种格式
        Integer bsr = null;
        String bsrCategory = null;
        String bsrSubcategory = null;
        Integer bsrSubcategoryRank = null;
        
                // 1. 查找表格形式的BSR信息(新格式)
        Elements bsrRows = doc.select("th:containsOwn(Best Sellers Rank), th:contains(Best Sellers Rank)");
        for (Element bsrHeader : bsrRows) {
            Element bsrCell = bsrHeader.nextElementSibling();
            if (bsrCell != null) {
                // 查找BSR列表项
                Elements bsrItems = bsrCell.select("li, .a-list-item");
                for (Element item : bsrItems) {
                    String itemText = item.text();
                    
                    // 解析主排名：#144,004 in Home & Kitchen (See Top 100...)
                    // 第一个包含"in"且包含括号的是主分类
                    if (itemText.contains("#") && itemText.contains(" in ") && itemText.contains("(") && bsr == null) {
                        String rankPart = itemText.substring(itemText.indexOf("#") + 1);
                        String[] parts = rankPart.split(" in ", 2);
                        if (parts.length >= 2) {
                            // 提取排名数字
                            String rankStr = parts[0].replaceAll("[^0-9,]", "").replaceAll(",", "");
                            try {
                                if (!rankStr.isEmpty()) {
                                    bsr = Integer.parseInt(rankStr);
                                    // 提取大类（括号前的部分）
                                    String categoryPart = parts[1];
                                    int parenIndex = categoryPart.indexOf('(');
                                    if (parenIndex > 0) {
                                        bsrCategory = categoryPart.substring(0, parenIndex).trim();
                                    } else {
                                        bsrCategory = categoryPart.trim();
                                    }
                                }
                            } catch (NumberFormatException ex) {
                                // ignore
                            }
                        }
                    }
                    
                    // 解析子分类排名：#423 in Home Office Desks
                    // 不包含括号的是子分类
                    else if (itemText.contains("#") && itemText.contains(" in ") && !itemText.contains("(")) {
                        String rankPart = itemText.substring(itemText.indexOf("#") + 1);
                        String[] parts = rankPart.split(" in ", 2);
                        if (parts.length >= 2) {
                            // 提取子分类排名数字
                            String subRankStr = parts[0].replaceAll("[^0-9,]", "").replaceAll(",", "");
                            try {
                                if (!subRankStr.isEmpty()) {
                                    bsrSubcategoryRank = Integer.parseInt(subRankStr);
                                    // 提取子分类名称
                                    bsrSubcategory = parts[1].trim();
                                }
                            } catch (NumberFormatException ex) {
                                // ignore
                            }
                        }
                    }
                }
                break; // 找到BSR信息后退出
            }
        }
        
        // 2. 回退到原有的解析方式（兼容旧格式）
        if (bsr == null) {
            Elements detailRows = doc.select("#detailBullets_feature_div .a-list-item, #productDetails_detailBullets_sections1 .a-list-item");
            for (Element row : detailRows) {
                String txt = row.text();
                String txtLower = txt.toLowerCase(Locale.ROOT);
                if (txtLower.contains("best sellers rank") || txtLower.contains("best seller rank") || txtLower.contains("best sellers")) {
                    // 提取主BSR排名（第一个数字）
                    String digits = txt.replaceAll("[^0-9,]", "").replaceAll(",", "");
                    try {
                        if (!digits.isEmpty()) bsr = Integer.parseInt(digits);
                    } catch (NumberFormatException ex) {
                        // ignore
                    }
                    
                    // 解析分类信息
                    if (txt.contains(" in ")) {
                        String[] parts = txt.split(" in ", 2);
                        if (parts.length > 1) {
                            String categoryPart = parts[1];
                            int parenIndex = categoryPart.indexOf('(');
                            if (parenIndex > 0) {
                                bsrCategory = categoryPart.substring(0, parenIndex).trim();
                            } else {
                                bsrCategory = categoryPart.trim();
                            }
                        }
                    }
                    break;
                }
            }
        }
        
        // 备用选择器：查找 "#SalesRank" 或其他BSR相关元素
        if (bsr == null) {
            Element sr = doc.selectFirst("#SalesRank, .rank");
            if (sr != null) {
                String txt = sr.text();
                String digits = txt.replaceAll("[^0-9,]", "").replaceAll(",", "");
                try { 
                    if (!digits.isEmpty()) bsr = Integer.parseInt(digits); 
                } catch (NumberFormatException ex) {}
                
                // 尝试从此元素也解析分类
                if (txt.contains(" in ") && bsrCategory == null) {
                    String[] parts = txt.split(" in ", 2);
                    if (parts.length > 1) {
                        bsrCategory = parts[1].split("[\\(\\)]")[0].trim();
                    }
                }
            }
        }
        
        s.setBsr(bsr);
        s.setBsrCategory(bsrCategory);
        s.setBsrSubcategory(bsrSubcategory);
        s.setBsrSubcategoryRank(bsrSubcategoryRank);
        // Debug日志
        System.out.println("[Parser DEBUG] BSR解析结果: bsr=" + bsr + ", category=" + bsrCategory + ", subcat=" + bsrSubcategory + ", subrank=" + bsrSubcategoryRank);

        // inventory: 更准确地解析库存信息
        Integer inventory = null;
        
        // 优先检查多个可能的库存相关选择器
        String[] inventorySelectors = {
            "#availability span", 
            "#availability_feature_div span",
            "#availability", 
            "#availability_feature_div",
            "[id*='availability']",
            ".a-size-medium.a-color-success",
            ".a-size-medium.a-color-price",
            "#merchant-info"
        };
        
        for (String selector : inventorySelectors) {
            Elements invNodes = doc.select(selector);
            for (Element invNode : invNodes) {
                String invText = invNode.text().toLowerCase(Locale.ROOT);
                
                // 检查常见的库存表达
                if (invText.contains("only") && invText.contains("left")) {
                    // 例如："Only 12 left in stock"
                    String digits = invText.replaceAll("[^0-9]", "");
                    try { 
                        if (!digits.isEmpty()) {
                            inventory = Integer.parseInt(digits);
                            break;
                        }
                    } catch (NumberFormatException ex) {}
                } else if (invText.matches(".*\\d+\\s*(left|remaining|available).*")) {
                    // 匹配包含数字和"left/remaining/available"的文本
                    String digits = invText.replaceAll("[^0-9]", "");
                    try { 
                        if (!digits.isEmpty()) {
                            inventory = Integer.parseInt(digits);
                            break;
                        }
                    } catch (NumberFormatException ex) {}
                } else if (invText.contains("in stock") && !invText.contains("out of stock")) {
                    // 有库存但没有具体数字，设为一个标识值（比如999表示有库存但不知道具体数量）
                    if (inventory == null) {
                        inventory = 999; // 表示有库存但数量未知
                    }
                } else if (invText.contains("out of stock") || invText.contains("unavailable") || 
                          invText.contains("temporarily out of stock")) {
                    inventory = 0;
                    break;
                }
            }
            if (inventory != null) break;
        }
        
        // 如果还没找到，尝试从购买选项中推断
        if (inventory == null) {
            Element buyBox = doc.selectFirst("#buybox, #desktop_buybox");
            if (buyBox != null) {
                if (buyBox.text().toLowerCase().contains("add to cart")) {
                    inventory = 999; // 有购买按钮，推断有库存
                } else if (buyBox.text().toLowerCase().contains("currently unavailable")) {
                    inventory = 0;
                }
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
            // 只选择 li 元素，避免同时选择 li 和其内部的 span.a-list-item 导致重复
            Elements bullets = doc.select("#feature-bullets li");
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

        // 评论总数与平均评分(常见选择器)
        try {
            // 评论总数: 查找 data-hook="total-review-count" 或 #acrCustomerReviewText
            String reviewsText = null;
            org.jsoup.nodes.Element revEl = doc.selectFirst("[data-hook=total-review-count], #acrCustomerReviewText");
            if (revEl != null) reviewsText = revEl.text();
            if (reviewsText != null) {
                // 从 "15 global ratings" 或 "15 ratings" 中提取数字
                String digits = reviewsText.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) s.setTotalReviews(Integer.parseInt(digits));
            }

            // 平均评分: 查找 data-hook="rating-out-of-text" 或 .a-icon-alt
            org.jsoup.nodes.Element avgEl = doc.selectFirst("[data-hook=rating-out-of-text], #averageCustomerReviews .a-icon-alt");
            if (avgEl != null) {
                String avgText = avgEl.text(); // "4.7 out of 5" or "4.7 out of 5 stars"
                // 提取第一个浮点数
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+)");
                java.util.regex.Matcher matcher = pattern.matcher(avgText);
                if (matcher.find()) {
                    s.setAvgRating(new java.math.BigDecimal(matcher.group(1)));
                }
            }
            // Debug日志
            System.out.println("[Parser DEBUG] 评论/评分解析结果: total_reviews=" + s.getTotalReviews() + ", avg_rating=" + s.getAvgRating());
        } catch (Exception ignored) {}

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
                sb.append("%02x".formatted(b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
