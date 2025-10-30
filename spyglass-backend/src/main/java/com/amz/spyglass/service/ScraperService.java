package com.amz.spyglass.service;

import com.amz.spyglass.scraper.Scraper;
import com.amz.spyglass.scraper.SeleniumScraper;
import com.amz.spyglass.scraper.JsoupScraper;
import com.amz.spyglass.alert.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 抓取服务（中文注释）
 * 说明：此 Service 作为上层对抓取能力的统一入口，内部委托到可插拔的 Scraper 实现（例如 JsoupScraper）。
 * 这样便于在单元测试中替换为 Mock 实现，或在生产中切换为 SeleniumScraper。
 */
@Service
public class ScraperService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperService.class);

    private final JsoupScraper jsoupScraper;
    private final SeleniumScraper seleniumScraper;
    private final AlertService alertService;

    public ScraperService(JsoupScraper jsoupScraper, SeleniumScraper seleniumScraper, AlertService alertService) {
        this.jsoupScraper = jsoupScraper;
        this.seleniumScraper = seleniumScraper;
        this.alertService = alertService;
    }

    /**
     * 抓取页面标题（静态抓取优先）
     */
    public String fetchTitleWithJsoup(String url) throws Exception {
        return jsoupScraper.fetchTitle(url);
    }

    /**
     * 抓取并返回完整的 Asin 快照：优先使用 Jsoup（轻量），当关键字段为空时回退到 Selenium 补全
     */
    public com.amz.spyglass.scraper.AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        logger.info("开始抓取: {}", url);
        com.amz.spyglass.scraper.AsinSnapshotDTO snap = jsoupScraper.fetchSnapshot(url);
        
        // 检查哪些关键字段为空
        boolean needSelenium = snap.getPrice() == null || snap.getBsr() == null || 
                              snap.getInventory() == null || snap.getTotalReviews() == null || 
                              snap.getAvgRating() == null || snap.getBulletPoints() == null;
        
        if (needSelenium) {
            logger.info("Jsoup抓取字段不完整，启用Selenium补全 - price:{}, bsr:{}, inventory:{}, reviews:{}, rating:{}, bullets:{}", 
                snap.getPrice() != null, snap.getBsr() != null, snap.getInventory() != null, 
                snap.getTotalReviews() != null, snap.getAvgRating() != null, snap.getBulletPoints() != null);
            
            try {
                com.amz.spyglass.scraper.AsinSnapshotDTO s2 = seleniumScraper.fetchSnapshot(url);
                
                // 合并非空字段（Selenium补全Jsoup缺失的部分）
                if (snap.getPrice() == null && s2.getPrice() != null) {
                    snap.setPrice(s2.getPrice());
                    logger.debug("Selenium补全价格: {}", s2.getPrice());
                }
                if (snap.getBsr() == null && s2.getBsr() != null) {
                    snap.setBsr(s2.getBsr());
                    logger.debug("Selenium补全BSR: {}", s2.getBsr());
                }
                if (snap.getInventory() == null && s2.getInventory() != null) {
                    snap.setInventory(s2.getInventory());
                    logger.debug("Selenium补全库存: {}", s2.getInventory());
                }
                if (snap.getTotalReviews() == null && s2.getTotalReviews() != null) {
                    snap.setTotalReviews(s2.getTotalReviews());
                    logger.debug("Selenium补全评论数: {}", s2.getTotalReviews());
                }
                if (snap.getAvgRating() == null && s2.getAvgRating() != null) {
                    snap.setAvgRating(s2.getAvgRating());
                    logger.debug("Selenium补全评分: {}", s2.getAvgRating());
                }
                if (snap.getBulletPoints() == null && s2.getBulletPoints() != null) {
                    snap.setBulletPoints(s2.getBulletPoints());
                    logger.debug("Selenium补全五点要点");
                }
                if (snap.getTitle() == null && s2.getTitle() != null) {
                    snap.setTitle(s2.getTitle());
                    logger.debug("Selenium补全标题");
                }
            } catch (Exception e) {
                logger.warn("Selenium补全失败: {}", e.getMessage());
            }
        }
        
        logger.info("抓取完成 - price:{}, bsr:{}, inventory:{}, reviews:{}, rating:{}", 
            snap.getPrice(), snap.getBsr(), snap.getInventory(), snap.getTotalReviews(), snap.getAvgRating());
        
        return snap;
    }
}

