package com.amz.spyglass.service;

import com.amz.spyglass.scraper.SeleniumScraper;
import com.amz.spyglass.scraper.JsoupScraper;
import com.amz.spyglass.scraper.HttpClientScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 亚马逊商品抓取服务
 * 
 * 提供统一的商品数据抓取接口，支持多种抓取策略：
 * 1. HttpClientScraper - 现代HTTP客户端，支持代理认证（优先使用）
 * 2. JsoupScraper - 轻量级HTML解析器（备用方案）  
 * 3. SeleniumScraper - 浏览器自动化，处理JavaScript渲染（最后备用）
 * 
 * 具备智能回退机制，当一种方式失败时自动尝试下一种方式
 * 集成数据持久化服务，支持历史数据存储和变更追踪
 * 
 * @author Spyglass Team
 * @version 2.0.0
 * @since 2024-12
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private final JsoupScraper jsoupScraper;
    private final SeleniumScraper seleniumScraper;
    private final HttpClientScraper httpClientScraper;

    public ScraperService(JsoupScraper jsoupScraper, SeleniumScraper seleniumScraper, 
                         HttpClientScraper httpClientScraper) {
        this.jsoupScraper = jsoupScraper;
        this.seleniumScraper = seleniumScraper;
        this.httpClientScraper = httpClientScraper;
        log.info("🚀 爬虫服务初始化完成 - 已加载HttpClient、Jsoup、Selenium三种抓取策略");
    }

    /**
     * 使用Jsoup抓取页面标题（静态抓取）
     * 
     * @param url 目标URL
     * @return 页面标题
     * @throws Exception 抓取异常
     */
    public String fetchTitleWithJsoup(String url) throws Exception {
        log.debug("📖 使用Jsoup抓取页面标题: {}", url);
        String title = jsoupScraper.fetchTitle(url);
        log.info("✅ 标题抓取成功: {}", title);
        return title;
    }

    /**
     * 智能抓取完整的ASIN产品快照数据
     * 
     * 采用三层抓取策略：
     * 1. HttpClientScraper - 首选方案，支持代理认证，性能最优
     * 2. JsoupScraper - 回退方案，用于静态内容解析  
     * 3. SeleniumScraper - 最后手段，补充动态内容（如库存信息）
     * 
     * @param url 亚马逊产品URL
     * @return 完整的产品数据快照
     * @throws Exception 抓取异常
     */
    public com.amz.spyglass.scraper.AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
<<<<<<< HEAD
        com.amz.spyglass.scraper.AsinSnapshotDTO snap = jsoupScraper.fetchSnapshot(url);
        boolean needSelenium = snap.getPrice() == null || snap.getBsr() == null || snap.getInventory() == null
                || snap.getTotalReviews() == null || snap.getAvgRating() == null || snap.getBulletPoints() == null;
        if (needSelenium) {
            try {
                com.amz.spyglass.scraper.AsinSnapshotDTO s2 = seleniumScraper.fetchSnapshot(url);
                if (snap.getPrice() == null && s2.getPrice() != null) snap.setPrice(s2.getPrice());
                if (snap.getBsr() == null && s2.getBsr() != null) snap.setBsr(s2.getBsr());
                if (snap.getInventory() == null && s2.getInventory() != null) snap.setInventory(s2.getInventory());
                if (snap.getTotalReviews() == null && s2.getTotalReviews() != null) snap.setTotalReviews(s2.getTotalReviews());
                if (snap.getAvgRating() == null && s2.getAvgRating() != null) snap.setAvgRating(s2.getAvgRating());
                if (snap.getBulletPoints() == null && s2.getBulletPoints() != null) snap.setBulletPoints(s2.getBulletPoints());
                if (snap.getImageMd5() == null && s2.getImageMd5() != null) snap.setImageMd5(s2.getImageMd5());
                if (snap.getAplusMd5() == null && s2.getAplusMd5() != null) snap.setAplusMd5(s2.getAplusMd5());
            } catch (Exception ignored) {}
=======
        log.info("🎯 开始智能抓取产品数据: {}", url);
        com.amz.spyglass.scraper.AsinSnapshotDTO snap = null;
        
        // 第一步：优先使用HttpClientScraper（支持代理认证）
        try {
            log.debug("🚀 尝试HttpClient抓取...");
            snap = httpClientScraper.fetchSnapshot(url);
            log.info("✅ HttpClient抓取成功 - 价格: {}, BSR: #{}", 
                    snap.getPrice(), snap.getBsr());
        } catch (Exception e) {
            log.warn("⚠️ HttpClient抓取失败，回退到Jsoup: {}", e.getMessage());
            // 第二步：回退到JsoupScraper
            try {
                snap = jsoupScraper.fetchSnapshot(url);
                log.info("✅ Jsoup回退抓取成功");
            } catch (Exception e2) {
                log.error("❌ Jsoup也失败了: {}", e2.getMessage());
                throw e2;
            }
>>>>>>> appmod/java-upgrade-20251031070753
        }
        
        // 第三步：如果库存信息缺失，使用Selenium补充
        if (snap.getInventory() == null || snap.getInventory() <= 0) {
            log.debug("🔄 库存信息缺失，尝试Selenium补充抓取...");
            try {
                com.amz.spyglass.scraper.AsinSnapshotDTO seleniumSnap = seleniumScraper.fetchSnapshot(url);
                if (seleniumSnap.getInventory() != null && seleniumSnap.getInventory() > 0) {
                    snap.setInventory(seleniumSnap.getInventory());
                    log.info("✅ Selenium成功补充库存信息: {}", snap.getInventory());
                } else {
                    log.warn("⚠️ Selenium也未能获取库存信息");
                }
            } catch (Exception e) {
                log.warn("⚠️ Selenium补充抓取失败: {}", e.getMessage());
            }
        }
        
        log.info("🎉 产品数据抓取完成 - 标题: {}, 价格: {}, BSR: #{} in {}", 
                snap.getTitle(), snap.getPrice(), snap.getBsr(), snap.getBsrCategory());
        return snap;
    }
    
    /**
     * 使用HttpClient抓取页面标题（支持代理认证）
     * 
     * @param url 目标URL
     * @return 页面标题
     * @throws Exception 抓取异常
     */
    public String fetchTitleWithHttpClient(String url) throws Exception {
        log.debug("🌐 使用HttpClient抓取页面标题: {}", url);
        String title = httpClientScraper.fetchTitle(url);
        log.info("✅ HttpClient标题抓取成功: {}", title);
        return title;
    }
}
