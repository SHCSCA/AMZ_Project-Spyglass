package com.amz.spyglass.service;

import com.amz.spyglass.scraper.AsinSnapshotDTO;
import com.amz.spyglass.scraper.JsoupScraper;
import com.amz.spyglass.scraper.SeleniumScraper;
import com.amz.spyglass.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 ScraperService 在 Jsoup 抓取字段缺失时是否正确使用 Selenium 结果补全。
 */
public class ScraperServiceMergeTest {

    @Test
    void shouldMergeMissingFieldsFromSelenium() throws Exception {
        // Mock Jsoup 返回部分字段为空
        JsoupScraper jsoup = Mockito.mock(JsoupScraper.class);
        SeleniumScraper selenium = Mockito.mock(SeleniumScraper.class);
        AlertService alertService = Mockito.mock(AlertService.class);

        AsinSnapshotDTO jsoupDto = new AsinSnapshotDTO();
        jsoupDto.setTitle("Sample Title");
        // price / bsr / inventory / reviews / rating 均为空
        Mockito.when(jsoup.fetchSnapshot("https://example.com"))
                .thenReturn(jsoupDto);

        AsinSnapshotDTO seleniumDto = new AsinSnapshotDTO();
        seleniumDto.setPrice(new BigDecimal("19.99"));
        seleniumDto.setBsr(123);
        seleniumDto.setInventory(7);
        seleniumDto.setTotalReviews(4567);
        seleniumDto.setAvgRating(new BigDecimal("4.5"));
        seleniumDto.setBulletPoints("Bullet1\nBullet2");
        Mockito.when(selenium.fetchSnapshot("https://example.com"))
                .thenReturn(seleniumDto);

        ScraperService service = new ScraperService(jsoup, selenium, alertService);
        AsinSnapshotDTO merged = service.fetchSnapshot("https://example.com");

        assertEquals(new BigDecimal("19.99"), merged.getPrice());
        assertEquals(123, merged.getBsr());
        assertEquals(7, merged.getInventory());
        assertEquals(4567, merged.getTotalReviews());
        assertEquals(new BigDecimal("4.5"), merged.getAvgRating());
        assertEquals("Bullet1\nBullet2", merged.getBulletPoints());
        assertEquals("Sample Title", merged.getTitle()); // 原始标题保留
    }
}

