package com.amz.spyglass.controller;

import com.amz.spyglass.service.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Tag(name = "测试接口", description = "提供用于开发和调试的临时测试端点")
public class TestController {

    private final ScraperService scraperService;

    public TestController(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @PostMapping("/scrape")
    @Operation(summary = "手动触发单个 URL 抓取", description = "这是一个测试端点，用于立即抓取指定的亚马逊产品页面URL，并返回抓取到的原始数据。")
    public ResponseEntity<?> testScrape(
            @Parameter(description = "要抓取的完整亚马逊产品URL", required = true, example = "https://www.amazon.com/dp/B08P3QVFMK")
            @RequestParam String url) {
        try {
            com.amz.spyglass.scraper.AsinSnapshotDTO snapshot = scraperService.fetchSnapshot(url);
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("抓取失败: " + e.getMessage());
        }
    }
}

