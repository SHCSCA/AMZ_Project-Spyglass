package com.amz.spyglass.service;

import com.amz.spyglass.scraper.Scraper;
import com.amz.spyglass.scraper.SeleniumScraper;
import com.amz.spyglass.scraper.JsoupScraper;
import com.amz.spyglass.alert.AlertService;
import org.springframework.stereotype.Service;

/**
 * 抓取服务（中文注释）
 * 说明：此 Service 作为上层对抓取能力的统一入口，内部委托到可插拔的 Scraper 实现（例如 JsoupScraper）。
 * 这样便于在单元测试中替换为 Mock 实现，或在生产中切换为 SeleniumScraper。
 */
@Service
public class ScraperService {

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
     * 抓取并返回完整的 Asin 快照：优先使用 Jsoup（轻量），当库存等关键字段为空时回退到 Selenium
     */
    public com.amz.spyglass.scraper.AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
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
        }
        return snap;
    }
}
