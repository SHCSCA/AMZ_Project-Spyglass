package com.amz.spyglass.service;

import com.amz.spyglass.scraper.Scraper;
import org.springframework.stereotype.Service;

/**
 * 抓取服务（中文注释）
 * 说明：此 Service 作为上层对抓取能力的统一入口，内部委托到可插拔的 Scraper 实现（例如 JsoupScraper）。
 * 这样便于在单元测试中替换为 Mock 实现，或在生产中切换为 SeleniumScraper。
 */
@Service
public class ScraperService {

    private final Scraper scraper;

    public ScraperService(Scraper scraper) {
        this.scraper = scraper;
    }

    /**
     * 抓取页面标题，委托给底层 Scraper 实现
     */
    public String fetchTitleWithJsoup(String url) throws Exception {
        return scraper.fetchTitle(url);
    }

    /**
     * 抓取并返回完整的 Asin 快照（委托给底层 Scraper）
     */
    public com.amz.spyglass.scraper.AsinSnapshot fetchSnapshot(String url) throws Exception {
        return scraper.fetchSnapshot(url);
    }
}

