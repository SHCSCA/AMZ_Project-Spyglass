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
        // 如果 inventory 未解析到则尝试用 Selenium 进行补抓
        if (snap.getInventory() == null) {
            try {
                com.amz.spyglass.scraper.AsinSnapshotDTO s2 = seleniumScraper.fetchSnapshot(url);
                // 合并非空字段
                if (s2.getInventory() != null) snap.setInventory(s2.getInventory());
            } catch (Exception ignored) {}
        }
        return snap;
    }
}

