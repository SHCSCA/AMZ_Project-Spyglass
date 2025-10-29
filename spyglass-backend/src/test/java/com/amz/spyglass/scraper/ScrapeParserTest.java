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
        AsinSnapshot s = ScrapeParser.parse(doc);

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
}
