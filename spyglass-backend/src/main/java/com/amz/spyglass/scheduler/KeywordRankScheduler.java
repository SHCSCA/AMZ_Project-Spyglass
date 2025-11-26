package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.AsinKeywords;
import com.amz.spyglass.repository.AsinKeywordsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private com.amz.spyglass.service.AsinKeywordsService asinKeywordsService;

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

        for (AsinKeywords keyword : keywordsToTrack) {
            try {
                asinKeywordsService.checkKeywordRank(keyword.getId());
            } catch (Exception e) {
                log.error("Failed to scrape keyword rank for keywordId={} asin='{}'", keyword.getId(), keyword.getAsin().getAsin(), e);
            }
        }
        log.info("Finished scheduled keyword rank scraping task.");
    }
}
