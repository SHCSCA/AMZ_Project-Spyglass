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
 * 页面解析器（中文注释）
 * 将 Jsoup 的 Document 解析成我们需要的字段（title、price、bsr、inventory、imageMd5、aplusMd5）。
 * 注意：解析规则使用启发式选择器，亚马逊页面多变，生产环境需根据实际页面调整或使用更稳健的策略。
 */
public class ScrapeParser {

    /**
     * 解析 Document 并返回填充的 AsinSnapshot（不包含 snapshotAt，调用方可设置）
     */
    public static AsinSnapshot parse(Document doc) {
        AsinSnapshot s = new AsinSnapshot();
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

        // BSR: 在详情或者排名区域查找 "Best Sellers Rank" 或者 "Best Sellers"
        Integer bsr = null;
        Elements detailBullets = doc.select("#detailBullets_feature_div li, #productDetails_detailBullets_sections1 tr");
        for (Element e : detailBullets) {
            String txt = e.text().toLowerCase(Locale.ROOT);
            if (txt.contains("best sellers rank") || txt.contains("best seller rank") || txt.contains("best sellers")) {
                String digits = txt.replaceAll("[^0-9,]", "");
                digits = digits.replaceAll(",", "");
                try {
                    if (!digits.isEmpty()) bsr = Integer.parseInt(digits);
                } catch (NumberFormatException ex) {
                    // ignore
                }
                break;
            }
        }
        // 备用选择器：查找 "#SalesRank"
        if (bsr == null) {
            Element sr = doc.selectFirst("#SalesRank");
            if (sr != null) {
                String txt = sr.text().replaceAll("[^0-9,]", "").replaceAll(",", "");
                try { if (!txt.isEmpty()) bsr = Integer.parseInt(txt); } catch (NumberFormatException ex) {}
            }
        }
        s.setBsr(bsr);

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

        // A+ 内容 MD5：尝试根据 aplus 区域获取 HTML 并计算 MD5（同样仅基于 HTML 字符串）
        Element aplus = doc.selectFirst("#aplus, .aplus, .a-plus");
        String aplusHtml = aplus == null ? null : aplus.html();
        s.setAplusMd5(aplusHtml == null ? null : md5Hex(aplusHtml));

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
