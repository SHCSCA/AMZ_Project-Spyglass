package com.amz.spyglass.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScrapeParser 单元测试（中文注释）
 * 使用本地 HTML 片段验证解析器对 title/price/bsr/inventory/imageMd5/aplusMd5 的提取。
 */
public class ScrapeParserTest {

    @Test
    public void parse_basicHtml_shouldExtractFields() {
        String html = "<html><head><title>Test Product</title></head>"
                + "<body>"
                + "<span id=\"priceblock_ourprice\">$12.34</span>"
                + "<div id=\"detailBullets_feature_div\"><ul><li>Best Sellers Rank: #1,234 in Books</li></ul></div>"
                + "<div id=\"availability\">In Stock.</div>"
                + "<div id=\"imgTagWrapperId\"><img src=\"https://example.com/image.jpg\" /></div>"
                + "<div id=\"aplus\"><p>A+</p></div>"
                + "</body></html>";

        Document doc = Jsoup.parse(html);
        AsinSnapshotDTO s = ScrapeParser.parse(doc);

        assertEquals("Test Product", s.getTitle());
        assertNotNull(s.getPrice());
        assertEquals(new BigDecimal("12.34"), s.getPrice());
        assertNotNull(s.getBsr());
        assertEquals(1234, s.getBsr());
        // inventory: In Stock 没有精确数字，解析器返回 null
        assertNull(s.getInventory());
        assertNotNull(s.getImageMd5());
        assertNotNull(s.getAplusMd5());
    }

    @Test
    public void parse_richHtml_shouldExtractExtendedFields() {
        String html = "<html><head><title>Fire TV Stick</title></head>"
                + "<body>"
                // 价格使用 a-price 结构
                + "<span class='a-price' data-a-color='price'><span class='a-offscreen'>$45.99</span></span>"
                // BSR 使用备用 SalesRank 节点
                + "<div id='SalesRank'>Best Sellers Rank: #2,345 in Electronics (See Top 100)</div>"
                // 库存提示仅剩
                + "<div id='availability'>Only 7 left in stock.</div>"
                // 主图
                + "<div id='imgTagWrapperId'><img src='https://cdn.example.com/prod/main-image.png' /></div>"
                // 五点要点
                + "<div id='feature-bullets'><ul>"
                + "<li class='a-list-item'>Fast streaming</li>"
                + "<li class='a-list-item'>Alexa Voice Remote</li>"
                + "<li class='a-list-item'>Dolby Atmos support</li>"
                + "</ul></div>"
                // A+ 内容块
                + "<div class='aplus'><div class='module'>Enhanced details</div></div>"
                // 评论总数
                + "<span id='acrCustomerReviewText'>1,234 ratings</span>"
                // 平均评分
                + "<span data-hook='rating-out-of-text'>4.6 out of 5</span>"
                // 差评区域（模拟两个评论，第二个为 2 星差评）
                + "<div class='a-section review'>"
                + "  <span class='a-icon-alt'>5.0 out of 5 stars</span>"
                + "  <span class='review-date'>Reviewed on Oct 1 2025</span>"
                + "  <span class='review-text'>Great product!</span>"
                + "</div>"
                + "<div class='a-section review'>"
                + "  <span class='a-icon-alt'>2.0 out of 5 stars</span>"
                + "  <span class='review-date'>Reviewed on Oct 2 2025</span>"
                + "  <span class='review-text'>Stopped working after a week.</span>"
                + "</div>"
                + "</body></html>";

        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        AsinSnapshotDTO s = ScrapeParser.parse(doc);

        // 标题
        assertEquals("Fire TV Stick", s.getTitle());
        // 价格
        assertEquals(new java.math.BigDecimal("45.99"), s.getPrice());
        // BSR
        assertEquals(2345, s.getBsr());
        // 库存（解析出仅剩数量）
        assertEquals(7, s.getInventory());
        // 主图 MD5
        assertNotNull(s.getImageMd5());
        // A+ 内容 MD5
        assertNotNull(s.getAplusMd5());
        // 五点要点包含三行
        assertNotNull(s.getBulletPoints());
        String[] bulletLines = s.getBulletPoints().split("\n");
        assertEquals(3, bulletLines.length);
        // 评论总数与评分
        assertEquals(1234, s.getTotalReviews());
        assertEquals(new java.math.BigDecimal("4.6"), s.getAvgRating());
        // 差评 MD5 应存在
        assertNotNull(s.getLatestNegativeReviewMd5());
    }

    @Test
    public void parse_mockAmazonProductHtml_shouldExtractAllKeyFields() throws Exception {
        String html = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/mock-amazon-product.html"));
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        AsinSnapshotDTO s = ScrapeParser.parse(doc);
        // 标题
        assertTrue(s.getTitle().contains("Sample Product Title"));
        // 价格
        assertEquals(new java.math.BigDecimal("59.99"), s.getPrice());
        // BSR
        assertEquals(1567, s.getBsr());
        // 库存（only 5 left -> 5）
        assertEquals(5, s.getInventory());
        // 五点要点
        assertNotNull(s.getBulletPoints());
        assertEquals(5, s.getBulletPoints().split("\n").length);
        // A+ 内容
        assertNotNull(s.getAplusMd5());
        // 评论总数与评分
        assertEquals(2345, s.getTotalReviews());
        assertEquals(new java.math.BigDecimal("4.4"), s.getAvgRating());
        // 差评 MD5
        assertNotNull(s.getLatestNegativeReviewMd5());
    }
}
