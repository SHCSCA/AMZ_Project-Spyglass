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
        }
        
        // 第三步：使用 Selenium 对关键缺失字段进行补全（仅补全缺失字段，避免重复）
        boolean needAnySupplement = snap.getPrice() == null || snap.getBsr() == null ||
                snap.getInventory() == null || snap.getTotalReviews() == null ||
                snap.getAvgRating() == null || snap.getBulletPoints() == null ||
                snap.getImageMd5() == null || snap.getAplusMd5() == null;

        if (needAnySupplement) {
            log.debug("🔄 发现关键字段缺失，尝试 Selenium 补全... (price? {} bsr? {} inv? {} reviews? {} rating? {} bullets? {} imgMd5? {} aplusMd5? {})",
                    snap.getPrice() == null, snap.getBsr() == null, snap.getInventory() == null,
                    snap.getTotalReviews() == null, snap.getAvgRating() == null, snap.getBulletPoints() == null,
                    snap.getImageMd5() == null, snap.getAplusMd5() == null);
            try {
                com.amz.spyglass.scraper.AsinSnapshotDTO seleniumSnap = seleniumScraper.fetchSnapshot(url);
                
                // 仅补全缺失的字段（避免覆盖已有数据）
                if (snap.getPrice() == null && seleniumSnap.getPrice() != null) snap.setPrice(seleniumSnap.getPrice());
                if (snap.getBsr() == null && seleniumSnap.getBsr() != null) snap.setBsr(seleniumSnap.getBsr());
                if (snap.getInventory() == null && seleniumSnap.getInventory() != null) snap.setInventory(seleniumSnap.getInventory());
                if (snap.getTotalReviews() == null && seleniumSnap.getTotalReviews() != null) snap.setTotalReviews(seleniumSnap.getTotalReviews());
                if (snap.getAvgRating() == null && seleniumSnap.getAvgRating() != null) snap.setAvgRating(seleniumSnap.getAvgRating());
                if (snap.getBulletPoints() == null && seleniumSnap.getBulletPoints() != null) snap.setBulletPoints(seleniumSnap.getBulletPoints());
                if (snap.getImageMd5() == null && seleniumSnap.getImageMd5() != null) snap.setImageMd5(seleniumSnap.getImageMd5());
                if (snap.getAplusMd5() == null && seleniumSnap.getAplusMd5() != null) snap.setAplusMd5(seleniumSnap.getAplusMd5());
                
                // 补全BSR分类字段（如果HttpClient/Jsoup没抓到）
                if (snap.getBsrCategory() == null && seleniumSnap.getBsrCategory() != null) snap.setBsrCategory(seleniumSnap.getBsrCategory());
                if (snap.getBsrSubcategory() == null && seleniumSnap.getBsrSubcategory() != null) snap.setBsrSubcategory(seleniumSnap.getBsrSubcategory());
                if (snap.getBsrSubcategoryRank() == null && seleniumSnap.getBsrSubcategoryRank() != null) snap.setBsrSubcategoryRank(seleniumSnap.getBsrSubcategoryRank());
                
                log.info("✅ Selenium 补全完成 -> price={} bsr={} bsrCat={} bsrSub={} bsrSubRank={} inv={} reviews={} rating={} bullets={} imgMd5={} aplusMd5={}",
                        snap.getPrice(), snap.getBsr(), snap.getBsrCategory(), snap.getBsrSubcategory(), snap.getBsrSubcategoryRank(),
                        snap.getInventory(), snap.getTotalReviews(), snap.getAvgRating(), 
                        snap.getBulletPoints() != null ? "Y" : "N",
                        snap.getImageMd5() != null ? "Y" : "N", snap.getAplusMd5() != null ? "Y" : "N");
            } catch (Exception e) {
                log.warn("⚠️ Selenium补全失败: {}", e.getMessage());
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
