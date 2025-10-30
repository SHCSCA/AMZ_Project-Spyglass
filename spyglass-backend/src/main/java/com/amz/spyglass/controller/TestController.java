package com.amz.spyglass.controller;

import com.amz.spyglass.service.ScraperService;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 提供手动触发抓取的测试端点，便于即时调试
 */
@Tag(name = "测试接口", description = "用于调试和测试的API端点")
@RestController
@RequestMapping("/api/test")
public class TestController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);
    
    private final ScraperService scraperService;
    
    public TestController(ScraperService scraperService) {
        this.scraperService = scraperService;
    }
    
    /**
     * 手动触发抓取测试
     * 
     * @param url 亚马逊商品URL
     * @return 抓取结果快照
     */
    @Operation(summary = "手动抓取测试", description = "立即抓取指定URL的商品数据，返回完整快照（用于调试）")
    @PostMapping("/scrape")
    public ResponseEntity<?> testScrape(
            @Parameter(description = "亚马逊商品URL", required = true, example = "https://www.amazon.com/dp/B0XXXXXXXXX")
            @RequestParam String url) {
        
        logger.info("收到测试抓取请求: {}", url);
        
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("timestamp", java.time.Instant.now());
        
        try {
            AsinSnapshotDTO snapshot = scraperService.fetchSnapshot(url);
            result.put("success", true);
            result.put("data", snapshot);
            
            // 添加字段完整性统计
            Map<String, Boolean> fieldStatus = new HashMap<>();
            fieldStatus.put("title", snapshot.getTitle() != null);
            fieldStatus.put("price", snapshot.getPrice() != null);
            fieldStatus.put("bsr", snapshot.getBsr() != null);
            fieldStatus.put("inventory", snapshot.getInventory() != null);
            fieldStatus.put("totalReviews", snapshot.getTotalReviews() != null);
            fieldStatus.put("avgRating", snapshot.getAvgRating() != null);
            fieldStatus.put("bulletPoints", snapshot.getBulletPoints() != null);
            fieldStatus.put("imageMd5", snapshot.getImageMd5() != null);
            fieldStatus.put("aplusMd5", snapshot.getAplusMd5() != null);
            result.put("fieldStatus", fieldStatus);
            
            logger.info("测试抓取成功: {} 字段", fieldStatus.values().stream().filter(v -> v).count());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("测试抓取失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    /**
     * 健康检查端点
     */
    @Operation(summary = "健康检查", description = "检查后端服务是否正常运行")
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", java.time.Instant.now());
        health.put("service", "Spyglass Backend");
        return ResponseEntity.ok(health);
    }
}
