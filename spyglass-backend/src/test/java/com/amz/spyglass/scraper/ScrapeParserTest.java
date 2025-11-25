package com.amz.spyglass.scraper;

import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ScrapeParserTest {

    private final ScrapeParser parser = new ScrapeParser();

    @Test
    void testParseInventoryFromAlert_Normal() {
        String alert = "This seller has only 5 of these available.";
        Optional<Integer> result = parser.parseInventoryFromAlert(alert);
        assertTrue(result.isPresent());
        assertEquals(5, result.get());
    }

    @Test
    void testParseInventoryFromAlert_Limit() {
        String alert = "This seller has a limit of 3 per customer.";
        Optional<Integer> result = parser.parseInventoryFromAlert(alert);
        // Should be empty because it's a limit, not stock
        assertFalse(result.isPresent(), "Should ignore limit warnings");
    }

    @Test
    void testParseInventoryFromAlert_AddedToCart() {
        String alert = "Added to Cart";
        Optional<Integer> result = parser.parseInventoryFromAlert(alert);
        assertTrue(result.isPresent());
        assertEquals(999, result.get());
    }

    @Test
    void testParseKeywordRank_Standard() {
        String html = """
                <html>
                <body>
                    <div data-component-type="s-search-result" data-asin="B001"></div>
                    <div data-component-type="s-search-result" data-asin="B002"></div>
                    <div data-component-type="s-search-result" data-asin="TARGET"></div>
                </body>
                </html>
                """;
        Document doc = Jsoup.parse(html);
        Optional<Integer> rank = parser.parseKeywordRank(doc, "TARGET");
        assertTrue(rank.isPresent());
        assertEquals(3, rank.get());
    }

    @Test
    void testParseKeywordRank_AlternativeSelector() {
        String html = """
                <html>
                <body>
                    <div class="s-result-item" data-asin="B001"></div>
                    <div class="s-result-item" data-asin="TARGET"></div>
                </body>
                </html>
                """;
        Document doc = Jsoup.parse(html);
        Optional<Integer> rank = parser.parseKeywordRank(doc, "TARGET");
        assertTrue(rank.isPresent());
        assertEquals(2, rank.get());
    }
    
    @Test
    void testParseKeywordRank_NotFound() {
        String html = """
                <html>
                <body>
                    <div data-component-type="s-search-result" data-asin="B001"></div>
                </body>
                </html>
                """;
        Document doc = Jsoup.parse(html);
        Optional<Integer> rank = parser.parseKeywordRank(doc, "TARGET");
        assertFalse(rank.isPresent());
    }
}
