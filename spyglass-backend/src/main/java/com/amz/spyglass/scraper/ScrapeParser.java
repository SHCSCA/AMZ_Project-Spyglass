package com.amz.spyglass.scraper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 亚马逊商品页面解析器
 * 负责从亚马逊商品详情页 HTML 中提取关键监控指标，包括价格、库存、BSR、促销等。
 * 支持多种亚马逊页面格式，并通过启发式选择器增强容错性。
 */
@Slf4j
@Component
public class ScrapeParser {

    // V2.1 F-DATA-001: 用于从加购提示中提取数字的正则表达式
    // "This seller has only 483 of these available." -> 483
    // "这位卖家最多只能为您提供 483 件商品" -> 483
    private static final Pattern INVENTORY_ALERT_PATTERN = Pattern.compile("(\\d{1,3}(,\\d{3})*|\\d+)(?!\\d*%)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})|\\d+\\.\\d{2}|\\d+)");
    private static final Pattern AVAILABLE_QTY_PATTERN = Pattern.compile("\\\"(?:availableQuantity|maxOrderQuantity|availableQty|remainingQty)\\\"\\s*:?\\s*(\\d+)");
    private static final Pattern COUPON_VALUE_PATTERN = Pattern.compile("(\\$\\d+(?:\\.\\d+)?|\\d+%)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


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
        String priceText = firstNonEmptyText(doc,
                "#corePriceDisplay_desktop_feature_div span.a-price span.a-offscreen",
                "#corePrice_feature_div span.a-price span.a-offscreen",
                "#corePrice_desktop span.a-price span.a-offscreen",
                "span[data-a-color=price] span.a-offscreen",
                "#price_inside_buybox",
                "#sns-base-price",
                "#priceblock_ourprice",
                "#priceblock_dealprice",
                ".a-price .a-offscreen",
                "#tp_price_block_total_price_ww");

        if (priceText == null) {
            priceText = firstAttributeValue(doc, "meta[property=og:price:amount]", "content");
        }
        if (priceText == null) {
            priceText = firstAttributeValue(doc, "span[data-a-size=xl][data-a-color=price]", "data-a-value");
        }

        BigDecimal parsedPrice = parsePriceToBigDecimal(priceText);
        if (parsedPrice == null) {
            parsedPrice = parsePriceFromDataMetrics(doc).orElse(null);
        }
        if (parsedPrice == null) {
            parsedPrice = parsePriceFromStructuredJson(doc).orElse(null);
        }
        s.setPrice(parsedPrice);

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
                            OptionalInt rankValue = parseFirstInteger(parts[0]);
                            if (rankValue.isPresent()) {
                                bsr = rankValue.getAsInt();
                                // 提取大类（括号前的部分）
                                String categoryPart = parts[1];
                                int parenIndex = categoryPart.indexOf('(');
                                if (parenIndex > 0) {
                                    bsrCategory = categoryPart.substring(0, parenIndex).trim();
                                } else {
                                    bsrCategory = categoryPart.trim();
                                }
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
                            OptionalInt subRank = parseFirstInteger(parts[0]);
                            if (subRank.isPresent()) {
                                bsrSubcategoryRank = subRank.getAsInt();
                                // 提取子分类名称
                                bsrSubcategory = parts[1].trim();
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
                    OptionalInt digits = parseFirstInteger(txt);
                    if (digits.isPresent()) {
                        bsr = digits.getAsInt();
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
                OptionalInt digits = parseFirstInteger(txt);
                if (digits.isPresent()) {
                    bsr = digits.getAsInt();
                }
                
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
        log.debug("[Parser] BSR解析结果: bsr={} category={} subcategory={} subRank={}", bsr, bsrCategory, bsrSubcategory, bsrSubcategoryRank);

        // inventory: 更准确地解析库存信息
        Integer inventory = null;
        boolean inferredInStock = false;
        
        // 优先检查多个可能的库存相关选择器
        String[] inventorySelectors = {
            "#availabilityInsideBuyBox_feature_div span",
            "#availabilityInsideBuyBox_feature_div",
            "#availability span", 
            "#availability_feature_div span",
            "#availability", 
            "#availability_feature_div",
            "[id*='availability']",
            ".a-size-medium.a-color-success",
            ".a-size-medium.a-color-price",
            "#merchant-info",
            "#availability-brief",
            "#deliveryBlockMessage",
            "#tabular-buybox .a-color-price",
            "#tabular-buybox .a-color-success"
        };
        
        for (String selector : inventorySelectors) {
            Elements invNodes = doc.select(selector);
            for (Element invNode : invNodes) {
                String invText = invNode.text().toLowerCase(Locale.ROOT);
                
                // 检查常见的库存表达
                if (invText.contains("only") && invText.contains("left")) {
                    // 例如："Only 12 left in stock"
                    OptionalInt parsed = parseFirstInteger(invText);
                    if (parsed.isPresent()) {
                        inventory = parsed.getAsInt();
                        break;
                    }
                } else if (invText.matches(".*\\d+\\s*(left|remaining|available).*")) {
                    // 匹配包含数字和"left/remaining/available"的文本
                    OptionalInt parsed = parseFirstInteger(invText);
                    if (parsed.isPresent()) {
                        inventory = parsed.getAsInt();
                        break;
                    }
                } else if (invText.contains("in stock") && !invText.contains("out of stock")) {
                    inferredInStock = true;
                } else if (invText.contains("out of stock") || invText.contains("unavailable") || 
                          invText.contains("temporarily out of stock")) {
                    inventory = 0;
                    break;
                }
            }
            if (inventory != null) break;
        }
        
        if (inventory == null) {
            inventory = extractInventoryFromQuantityInputs(doc);
        }

        if (inventory == null) {
            inventory = extractInventoryFromEmbeddedScripts(doc);
        }

        // 如果还没找到，尝试从购买选项中推断
        if (inventory == null) {
            Element buyBox = doc.selectFirst("#buybox, #desktop_buybox");
            if (buyBox != null) {
                String buyBoxText = buyBox.text().toLowerCase(Locale.ROOT);
                if (buyBoxText.contains("add to cart")) {
                    inferredInStock = true;
                } else if (buyBox.text().toLowerCase().contains("currently unavailable")) {
                    inventory = 0;
                }
            }
        }

        if (inventory == null && inferredInStock) {
            log.debug("[Parser] 检测到有货提示但未解析到具体库存，等待 999 加购策略补全");
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
        } catch (RuntimeException e) {
            log.warn("解析要点列表失败", e);
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
                OptionalInt totalReviews = parseFirstInteger(reviewsText);
                totalReviews.ifPresent(value -> s.setTotalReviews(value));
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
            log.debug("[Parser] 评论/评分解析结果: totalReviews={} avgRating={}", s.getTotalReviews(), s.getAvgRating());
        } catch (RuntimeException e) {
            log.warn("解析评论与评分信息失败", e);
        }

        // 尝试抓取最近的差评（1-3星），选择评论列表中第一个满足条件的项
        try {
            org.jsoup.select.Elements reviewEls = doc.select(".review, .a-section.review");
            for (org.jsoup.nodes.Element re : reviewEls) {
                String ratingText = null;
                org.jsoup.nodes.Element r = re.selectFirst(".a-icon-alt, .review-rating");
                if (r != null) ratingText = r.text();
                if (ratingText != null) {
                    OptionalInt ratingDigits = parseFirstInteger(ratingText);
                    if (ratingDigits.isPresent()) {
                        int rating = ratingDigits.getAsInt();
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
        } catch (RuntimeException e) {
            log.warn("解析差评信息失败", e);
        }

        // V2.1 F-DATA-002: 解析促销信息
            String contextId = Optional.ofNullable(doc.baseUri()).orElse(title);
            log.debug("开始解析促销信息, source={}", contextId);
            s.setCouponValue(parseCoupon(doc).orElse(null));
            s.setLightningDeal(parseLightningDeal(doc));
            if (s.getCouponValue() != null || s.isLightningDeal()) {
                log.info("检测到促销活动 source={} coupon={} lightningDeal={}", contextId, s.getCouponValue(), s.isLightningDeal());
        }

        return s;
    }

    private static BigDecimal parsePriceToBigDecimal(String priceText) {
        if (priceText == null) return null;
        String normalized = priceText.replace('\u00a0', ' ');
        Matcher priceMatcher = PRICE_PATTERN.matcher(normalized);
        if (priceMatcher.find()) {
            String number = priceMatcher.group(1).replace(",", "");
            try {
                return new BigDecimal(number);
            } catch (NumberFormatException ignored) {
                // ignore and fall through
            }
        }
        return null;
    }

    private static Optional<BigDecimal> parsePriceFromDataMetrics(Document doc) {
        Element metrics = doc.selectFirst("#cerberus-data-metrics");
        if (metrics != null) {
            BigDecimal price = parsePriceToBigDecimal(metrics.attr("data-asin-price"));
            if (price != null) {
                return Optional.of(price);
            }
        }
        Element priceAttr = doc.selectFirst("[data-asin-price]");
        if (priceAttr != null) {
            BigDecimal price = parsePriceToBigDecimal(priceAttr.attr("data-asin-price"));
            if (price != null) {
                return Optional.of(price);
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> parsePriceFromStructuredJson(Document doc) {
        Elements ldScripts = doc.select("script[type=application/ld+json]");
        for (Element script : ldScripts) {
            String data = script.data();
            if (isBlank(data)) {
                continue;
            }
            try {
                JsonNode root = OBJECT_MAPPER.readTree(data);
                BigDecimal price = extractPriceFromOffersNode(root.path("offers"));
                if (price != null) {
                    return Optional.of(price);
                }
            } catch (Exception e) {
                log.debug("解析 ld+json 价格失败: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static BigDecimal extractPriceFromOffersNode(JsonNode offersNode) {
        if (offersNode == null || offersNode.isMissingNode()) {
            return null;
        }
        if (offersNode.isArray()) {
            for (JsonNode node : offersNode) {
                BigDecimal price = extractPriceFromOffersNode(node);
                if (price != null) {
                    return price;
                }
            }
            return null;
        }
        if (offersNode.has("price")) {
            return parsePriceToBigDecimal(offersNode.get("price").asText());
        }
        if (offersNode.has("lowPrice")) {
            return parsePriceToBigDecimal(offersNode.get("lowPrice").asText());
        }
        JsonNode priceSpec = offersNode.path("priceSpecification");
        if (!priceSpec.isMissingNode() && priceSpec.has("price")) {
            return parsePriceToBigDecimal(priceSpec.get("price").asText());
        }
        return null;
    }

    private static Integer extractInventoryFromQuantityInputs(Document doc) {
        Element quantityInput = doc.selectFirst("input#quantity, input[name=quantity], input#mobileQuantityStepper-input, input[data-max]");
        if (quantityInput != null) {
            String candidate = firstNonEmptyAttr(quantityInput, "data-a-max-quantity", "data-max", "max");
            if (!isBlank(candidate)) {
                OptionalInt parsed = parseFirstInteger(candidate);
                if (parsed.isPresent()) {
                    return parsed.getAsInt();
                }
            }
        }

        Element hiddenStock = doc.selectFirst("input[id*=availableQuantity], input[name=available_qty], input[data-available], span[data-available]");
        if (hiddenStock != null) {
            String candidate = firstNonEmptyAttr(hiddenStock, "value", "data-available", "data-default-available");
            if (!isBlank(candidate)) {
                OptionalInt parsed = parseFirstInteger(candidate);
                if (parsed.isPresent()) {
                    return parsed.getAsInt();
                }
            }
        }
        return null;
    }

    private static Integer extractInventoryFromEmbeddedScripts(Document doc) {
        String html = doc.outerHtml();
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher matcher = AVAILABLE_QTY_PATTERN.matcher(html);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String firstNonEmptyText(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String text = element.text();
            if (!isBlank(text)) {
                return text.trim();
            }
            String valueAttr = element.attr("value");
            if (!isBlank(valueAttr)) {
                return valueAttr.trim();
            }
        }
        return null;
    }

    private static String firstAttributeValue(Document doc, String selector, String attr) {
        Element element = doc.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String value = element.attr(attr);
        return isBlank(value) ? null : value.trim();
    }

    private static String firstNonEmptyAttr(Element element, String... attrNames) {
        for (String attr : attrNames) {
            if (attr == null) {
                continue;
            }
            String value = element.attr(attr);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static OptionalInt parseFirstInteger(String text) {
        if (text == null || text.isEmpty()) {
            return OptionalInt.empty();
        }
        long value = 0;
        boolean collecting = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                collecting = true;
                value = value * 10 + (ch - '0');
                if (value > Integer.MAX_VALUE) {
                    log.warn("解析整数时检测到溢出，原始文本={}", text);
                    return OptionalInt.empty();
                }
            } else if (ch == ',' && collecting) {
                // thousands separator, skip
            } else if (collecting) {
                return OptionalInt.of((int) value);
            }
        }
        return collecting ? OptionalInt.of((int) value) : OptionalInt.empty();
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

    /**
     * V2.1 F-DATA-002: 解析优惠券信息
     * 亚马逊页面通常将优惠券信息放在一个带有特定 CSS class 的 span 中。
     * 例如: <span class="promoPriceBlockMessage">...</span> or <label ... for="coupon-checkbox">...</label>
     *
     * @param doc Jsoup Document
     * @return Optional<String> 包含优惠券面额文本, 如 "$10.00 off" 或 "5% off"
     */
    private static Optional<String> parseCoupon(Document doc) {
        String[] selectors = new String[] {
                "label[for*=coupon]",
                "span.promoPriceBlockMessage",
                "#coupon-badge",
                "#couponBadge",
                ".couponBadge",
                "#promoPriceBlockMessage_feature_div",
                ".a-color-success"
        };
        for (String selector : selectors) {
            Elements candidates = doc.select(selector);
            for (Element candidate : candidates) {
                Optional<String> normalized = normalizeCouponText(candidate.text());
                if (normalized.isPresent()) {
                    log.debug("通过选择器 {} 捕获优惠券文本: {}", selector, normalized.get());
                    return normalized;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * V2.1 F-DATA-002: 检测是否正在进行秒杀 (Lightning Deal)
     * 秒杀活动通常有特定的ID或CSS class，例如包含 "dealBadge" 或 "lightningDeal" 字符串的元素。
     *
     * @param doc Jsoup Document
     * @return boolean 如果找到秒杀标识则返回 true
     */
    private static boolean parseLightningDeal(Document doc) {
        // 策略: 查找包含 "deal" 或 "limited time" 等关键词的元素
        // 这也是一个需要根据实际页面结构灵活调整的选择器
        String[] selectors = new String[] {
                "#dealBadge_feature_div",
                "[id*=dealBadge]",
                "[data-deal-id]",
                ".dealBadge",
                ".savingsBadge"
        };
        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            for (Element el : elements) {
                String elementText = el.text().toLowerCase(Locale.ROOT);
                if (elementText.contains("deal") || elementText.contains("limited time") || elementText.contains("lightning")) {
                    log.debug("找到疑似 Deal 元素: {}", elementText);
                    return true;
                }
            }
        }
        return !doc.select("span:matchesOwn((?i)lightning deal)").isEmpty();
    }

    private static Optional<String> normalizeCouponText(String rawText) {
        if (isBlank(rawText)) {
            return Optional.empty();
        }
        Matcher matcher = COUPON_VALUE_PATTERN.matcher(rawText);
        if (matcher.find()) {
            String value = matcher.group(1);
            return Optional.of(value + " off");
        }
        String lower = rawText.toLowerCase(Locale.ROOT);
        if (lower.contains("coupon") || lower.contains("save")) {
            return Optional.of(rawText.trim());
        }
        return Optional.empty();
    }

    /**
     * V2.1 F-DATA-001: 从“999加购法”的提示文本中解析真实库存
     *
     * @param alertText 从购物车页面捕获的提示信息
     * @return Optional<Integer> 包含解析出的库存数量，如果未找到则为空
     */
    public Optional<Integer> parseInventoryFromAlert(String alertText) {
        if (alertText == null || alertText.isEmpty()) {
            return Optional.empty();
        }

        String lowerCaseText = alertText.toLowerCase(Locale.ROOT);

        // V2.1 F-DATA-001 Hotfix: 检查是否存在限购关键词，如果存在，则不应将限购数量误判为库存
        if (lowerCaseText.contains("limit") || lowerCaseText.contains("per customer") || lowerCaseText.contains("限购")) {
            log.warn("检测到卖家可能设置了限购，提示文本: '{}'。此数字不代表总库存，将忽略。", alertText);
            // 当检测到购买限制时，我们无法从该警报中确定真实库存。
            // 返回 empty 将允许其他库存检查机制（如果存在）继续进行。
            return Optional.empty();
        }

        Matcher matcher = INVENTORY_ALERT_PATTERN.matcher(alertText);
        if (matcher.find()) {
            OptionalInt parsed = parseFirstInteger(matcher.group(1));
            if (parsed.isPresent()) {
                int parsedValue = parsed.getAsInt();
                log.debug("从库存提示 '{}' 中解析出数字: {}", alertText, parsedValue);
                return Optional.of(parsedValue);
            }
            log.error("解析库存数字失败: '{}'", alertText);
        } else {
            // PRD F-DATA-001 异常处理: 如果允许购买999，则标记为999+
            // 这里通过检查文本是否包含 "added to cart" 或类似成功信息来判断
            if (lowerCaseText.contains("added to cart") || lowerCaseText.contains("已加入购物车")) {
                 log.info("未找到库存限制提示，且加购成功，标记库存为 999+");
                 return Optional.of(999); // 使用 999 代表库存充足
            }
        }

        log.warn("未能在提示文本中找到明确的库存数量: '{}'", alertText);
        return Optional.empty();
    }

    /**
     * V2.1 F-BIZ-001: 从亚马逊搜索结果页面解析指定 ASIN 的页内排名（仅当前页）。
     * @param doc 搜索结果页 Document
     * @param targetAsin 目标 ASIN
     * @return Optional<Integer> 1-based 排名
     */
    public Optional<Integer> parseKeywordRank(Document doc, String targetAsin) {
        if (doc == null || targetAsin == null || targetAsin.isBlank()) {
            return Optional.empty();
        }
        String target = targetAsin.trim();

        // 优先使用 data-component-type='s-search-result'
        Elements results = doc.select("[data-component-type='s-search-result'][data-asin]");
        
        // 回退策略
        if (results.isEmpty()) {
            results = doc.select("div.s-result-item[data-asin]:not([data-asin=''])");
        }

        log.debug("关键词排名解析: 检测到 {} 个搜索结果条目", results.size());
        
        // 收集当前页所有 ASIN 用于调试 (仅在 DEBUG 级别)
        if (log.isDebugEnabled()) {
            String asinsOnPage = results.stream()
                    .map(e -> e.attr("data-asin"))
                    .limit(20)
                    .collect(java.util.stream.Collectors.joining(","));
            log.debug("当前页前20个ASIN: [{}]", asinsOnPage);
        }

        for (int i = 0; i < results.size(); i++) {
            Element item = results.get(i);
            String asin = item.attr("data-asin");
            if (asin != null && asin.trim().equalsIgnoreCase(target)) {
                int rank = i + 1;
                log.info("目标 ASIN={} 在当前搜索结果页内排名={}", target, rank);
                return Optional.of(rank);
            }
        }
        log.debug("目标 ASIN={} 未出现在当前搜索结果页", target);
        return Optional.empty();
    }

    /**
     * V2.1 F-BIZ-001: 从预先筛选的搜索结果列表中解析指定 ASIN 的排名。
     * @param results 搜索结果元素列表
     * @param targetAsin 目标 ASIN
     * @return Optional<Integer> 1-based 排名
     */
    public Optional<Integer> parseKeywordRank(java.util.List<Element> results, String targetAsin) {
        if (results == null || results.isEmpty() || targetAsin == null || targetAsin.isBlank()) {
            return Optional.empty();
        }
        String target = targetAsin.trim();

        log.debug("关键词排名解析: 在 {} 个结果中查找 ASIN={}", results.size(), target);

        for (int i = 0; i < results.size(); i++) {
            Element item = results.get(i);
            String asin = item.attr("data-asin");
            if (asin != null && asin.trim().equalsIgnoreCase(target)) {
                int rank = i + 1;
                log.info("目标 ASIN={} 在当前结果列表中排名={}", target, rank);
                return Optional.of(rank);
            }
        }
        log.debug("目标 ASIN={} 未出现在当前结果列表中", target);
        return Optional.empty();
    }

    /**
     * 从文档中选择搜索结果元素，包含回退策略。
     * @param doc Jsoup Document
     * @return 搜索结果元素列表
     */
    public java.util.List<Element> selectSearchResults(Document doc) {
        if (doc == null) return new java.util.ArrayList<>();
        
        // 优先使用 data-component-type='s-search-result'
        Elements results = doc.select("[data-component-type='s-search-result'][data-asin]");
        
        // 回退策略
        if (results.isEmpty()) {
            results = doc.select("div.s-result-item[data-asin]:not([data-asin=''])");
        }
        return results;
    }
}
