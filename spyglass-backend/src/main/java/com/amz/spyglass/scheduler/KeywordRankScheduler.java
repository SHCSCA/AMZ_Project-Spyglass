package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.AsinKeywords;
import com.amz.spyglass.model.KeywordRankHistory;
import com.amz.spyglass.repository.AsinKeywordsRepository;
import com.amz.spyglass.repository.KeywordRankHistoryRepository;
import com.amz.spyglass.scraper.SeleniumScraper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * V2.1 F-BIZ-001: 定时任务，用于抓取关键词排名。
 */
@Slf4j
@Component
public class KeywordRankScheduler {

    @Autowired
    private AsinKeywordsRepository asinKeywordsRepository;

    @Autowired
    private KeywordRankHistoryRepository keywordRankHistoryRepository;

    @Autowired
    private SeleniumScraper seleniumScraper;

    /**
     * 每天凌晨2点执行关键词排名抓取。
     * 使用分布式锁可以防止多实例重复执行，这里暂时简化。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void scrapeKeywordRanks() {
        log.info("Starting scheduled keyword rank scraping task.");
        List<AsinKeywords> keywordsToTrack = asinKeywordsRepository.findByIsTrackedTrue();

        if (keywordsToTrack.isEmpty()) {
            log.info("No keywords are marked for tracking. Skipping task.");
            return;
        }

        log.info("Found {} keywords to track.", keywordsToTrack.size());
        LocalDate today = LocalDate.now();

        for (AsinKeywords keyword : keywordsToTrack) {
            try {
                log.debug("Scraping rank for ASIN '{}' with keyword '{}'", keyword.getAsin(), keyword.getKeyword());
                SeleniumScraper.KeywordRankResult rankResult = seleniumScraper.fetchKeywordRank(keyword.getAsin(), keyword.getKeyword());

                KeywordRankHistory history = new KeywordRankHistory(
                        keyword,
                        today,
                        rankResult.getNaturalRank(),
                        rankResult.getSponsoredRank(),
                        rankResult.getPage()
                );

                keywordRankHistoryRepository.save(history);
                log.info("Successfully scraped and saved rank for ASIN '{}', keyword '{}'. Natural Rank: {}, Sponsored Rank: {}, Page: {}",
                        keyword.getAsin(), keyword.getKeyword(), rankResult.getNaturalRank(), rankResult.getSponsoredRank(), rankResult.getPage());

            } catch (Exception e) {
                log.error("Failed to scrape keyword rank for ASIN '{}', keyword '{}'", keyword.getAsin(), keyword.getKeyword(), e);
                // 创建一个失败记录
                KeywordRankHistory failedHistory = new KeywordRankHistory(keyword, today, -1, -1, -1);
                keywordRankHistoryRepository.save(failedHistory);
            }
        }
        log.info("Finished scheduled keyword rank scraping task.");
    }
}
