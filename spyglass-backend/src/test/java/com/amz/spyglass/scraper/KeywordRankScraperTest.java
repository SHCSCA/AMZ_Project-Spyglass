package com.amz.spyglass.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class KeywordRankScraperTest {

    private JsoupScraper jsoupScraper;
    private KeywordRankScraper keywordRankScraper;

    @BeforeEach
    public void setUp() {
        jsoupScraper = mock(JsoupScraper.class);
        keywordRankScraper = new KeywordRankScraper(jsoupScraper);
    }

    @Test
    public void testFetchKeywordRank_Found() throws IOException {
        // Load sample HTML
        File htmlFile = new ClassPathResource("sample-search-result.html").getFile();
        Document doc = Jsoup.parse(htmlFile, "UTF-8");

        // Mock JsoupScraper to return this document
        when(jsoupScraper.getDocument(anyString())).thenReturn(Optional.of(doc));

        // Test
        KeywordRankResult result = keywordRankScraper.fetchKeywordRank("test keyword", "B00TEST002", "US");

        assertTrue(result.isFound(), "Rank should be found");
        assertEquals(2, result.getNaturalRank(), "Rank should be 2");
    }

    @Test
    public void testFetchKeywordRank_NotFound() throws IOException {
        // Load sample HTML
        File htmlFile = new ClassPathResource("sample-search-result.html").getFile();
        Document doc = Jsoup.parse(htmlFile, "UTF-8");

        // Mock JsoupScraper to return this document
        when(jsoupScraper.getDocument(anyString())).thenReturn(Optional.of(doc));

        // Test for an ASIN that is not in the list
        KeywordRankResult result = keywordRankScraper.fetchKeywordRank("test keyword", "B00MISSING", "US");

        assertTrue(!result.isFound(), "Rank should not be found");
    }
}
