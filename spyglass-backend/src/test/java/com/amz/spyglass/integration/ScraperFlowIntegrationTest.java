package com.amz.spyglass.integration;

import com.amz.spyglass.alert.AlertService;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import com.amz.spyglass.service.ScraperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成级（伪）测试：验证 ScraperService 在 Jsoup 缺失关键字段时使用 Selenium 补全并合并结果。
 * 不访问真实网络，使用 Mock，验证价格、BSR、库存、评论数、评分、要点补全逻辑。
 */
public class ScraperFlowIntegrationTest {

    private ScraperService scraperService;
    private AlertService alertService;
    private com.amz.spyglass.scraper.JsoupScraper jsoup;
    private com.amz.spyglass.scraper.SeleniumScraper selenium;

    @BeforeEach
    void setup() {
        jsoup = Mockito.mock(com.amz.spyglass.scraper.JsoupScraper.class);
        selenium = Mockito.mock(com.amz.spyglass.scraper.SeleniumScraper.class);
        alertService = Mockito.mock(AlertService.class);
        scraperService = new ScraperService(jsoup, selenium, alertService);
    }

    @Test
    void shouldMergeAndNotCallAlertServiceDirectly() throws Exception {
        String url = "https://www.amazon.com/dp/TESTASIN";
        AsinSnapshotDTO jsoupDto = new AsinSnapshotDTO();
        jsoupDto.setTitle("Title From Jsoup"); // 其它关键字段为空
        Mockito.when(jsoup.fetchSnapshot(url)).thenReturn(jsoupDto);

        AsinSnapshotDTO seleniumDto = new AsinSnapshotDTO();
        seleniumDto.setPrice(new BigDecimal("15.99"));
        seleniumDto.setBsr(222);
        seleniumDto.setInventory(9);
        seleniumDto.setTotalReviews(321);
        seleniumDto.setAvgRating(new BigDecimal("4.3"));
        seleniumDto.setBulletPoints("BP1\nBP2");
        Mockito.when(selenium.fetchSnapshot(url)).thenReturn(seleniumDto);

        AsinSnapshotDTO merged = scraperService.fetchSnapshot(url);
        assertEquals(new BigDecimal("15.99"), merged.getPrice());
        assertEquals(222, merged.getBsr());
        assertEquals(9, merged.getInventory());
        assertEquals(321, merged.getTotalReviews());
        assertEquals(new BigDecimal("4.3"), merged.getAvgRating());
        assertEquals("BP1\nBP2", merged.getBulletPoints());

        // 验证 AlertService 未被直接调用（调度器层才调用 compareAndAlert）
        Mockito.verify(alertService, Mockito.never()).compareAndAlert(Mockito.any(), Mockito.any());
    }
}
