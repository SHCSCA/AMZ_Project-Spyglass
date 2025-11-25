package com.amz.spyglass.scheduler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 轻量调度器：仅负责批量读取 ASIN 并提交到独立的执行器（ScraperTaskExecutor）。
 */
@Component
@Profile("!manualtest")
@Slf4j
@RequiredArgsConstructor
public class ScraperScheduler {

    private final AsinRepository asinRepository;
    private final com.amz.spyglass.repository.AsinKeywordsRepository asinKeywordsRepository;
    private final ScraperTaskExecutor scraperTaskExecutor;

    @Value("${scraper.fixedDelayMs:14400000}")
    private long configuredDelay;

    // 定时任务：每天 UTC 00:01 执行一次；另外在启动后 10 秒执行一次（用于首次初始化）
    @Scheduled(cron = "0 1 0 * * ?", zone = "UTC") // 修改为每天 00:01 UTC 执行
    @Scheduled(initialDelay = 10000, fixedDelay = Long.MAX_VALUE)
    public void runAll() {
        long start = System.currentTimeMillis();
        log.info("========================================");
        log.info("[Scheduler] 开始批量调度抓取任务（启动时立即执行 + 每天UTC 00:01）...");
        List<AsinModel> all = asinRepository.findAll();
        int totalCount = all.size();
        int submittedCount = 0;
        int failedCount = 0;
        log.info("[Scheduler] 准备调度 ASIN 总数: {}", totalCount);
        for (AsinModel asin : all) {
            try {
                log.info("[Scheduler] 提交异步抓取任务 -> ASIN={}, Site={}, ID={}, Nickname={}",
                        asin.getAsin(), asin.getSite(), asin.getId(), asin.getNickname());
                runForAsinAsync(asin.getId());
                submittedCount++;
            } catch (Exception ex) {
                failedCount++;
                log.error("[Scheduler] 提交异步抓取失败 ASIN={} (Site={}, ID={}) : {}",
                        asin.getAsin(), asin.getSite(), asin.getId(), ex.getMessage(), ex);
            }
        }
        long duration = System.currentTimeMillis() - start;
        log.info("========================================");
        log.info("[Scheduler] 批量调度完成");
        log.info("[Scheduler] 总数: {}, 已提交: {}, 提交失败: {}, 耗时: {} ms",
                totalCount, submittedCount, failedCount, duration);
        log.info("========================================");
    }

    // 关键词排名监控定时任务：每天 UTC 00:30 执行（错开 ASIN 抓取高峰）
    @Scheduled(cron = "0 30 0 * * ?", zone = "UTC")
    public void runAllKeywords() {
        long start = System.currentTimeMillis();
        log.info("========================================");
        log.info("[Scheduler] 开始批量调度关键词排名监控任务...");
        
        List<com.amz.spyglass.model.AsinKeywords> trackedKeywords = asinKeywordsRepository.findByIsTrackedTrue();
        int totalCount = trackedKeywords.size();
        int submittedCount = 0;
        int failedCount = 0;
        
        log.info("[Scheduler] 准备调度关键词总数: {}", totalCount);
        
        for (com.amz.spyglass.model.AsinKeywords kw : trackedKeywords) {
            try {
                log.info("[Scheduler] 提交关键词排名抓取任务 -> ID={}, Keyword={}, ASIN={}",
                        kw.getId(), kw.getKeyword(), kw.getAsin().getAsin());
                scraperTaskExecutor.executeKeywordRankCheckAsync(kw.getId());
                submittedCount++;
            } catch (Exception ex) {
                failedCount++;
                log.error("[Scheduler] 提交关键词任务失败 ID={} : {}", kw.getId(), ex.getMessage(), ex);
            }
        }
        
        long duration = System.currentTimeMillis() - start;
        log.info("========================================");
        log.info("[Scheduler] 关键词排名调度完成");
        log.info("[Scheduler] 总数: {}, 已提交: {}, 提交失败: {}, 耗时: {} ms",
                totalCount, submittedCount, failedCount, duration);
        log.info("========================================");
    }

    public void runForAsinAsync(Long asinId) throws Exception {
        scraperTaskExecutor.executeForAsinAsync(asinId);
    }

    public int runForSpecificAsins(java.util.List<Long> asinIds) {
        if (asinIds == null || asinIds.isEmpty()) {
            log.warn("[Scheduler] 手动抓取请求为空，跳过");
            return 0;
        }
        log.info("========================================");
        log.info("[Scheduler] 手动触发指定 ASIN 抓取，数量: {}", asinIds.size());
        log.info("========================================");
        int submittedCount = 0;
        int failedCount = 0;
        for (Long asinId : asinIds) {
            try {
                java.util.Optional<AsinModel> asinOpt = asinRepository.findById(asinId);
                if (asinOpt.isEmpty()) {
                    log.warn("[Scheduler] ASIN ID={} 不存在，跳过", asinId);
                    failedCount++;
                    continue;
                }
                AsinModel asin = asinOpt.get();
                log.info("[Scheduler] 手动提交抓取任务 -> ASIN={}, Site={}, ID={}",
                        asin.getAsin(), asin.getSite(), asin.getId());
                runForAsinAsync(asinId);
                submittedCount++;
            } catch (Exception ex) {
                failedCount++;
                log.error("[Scheduler] 手动提交抓取失败 ASIN_ID={} : {}", asinId, ex.getMessage(), ex);
            }
        }
        log.info("========================================");
        log.info("[Scheduler] 手动抓取提交完成 - 总数: {}, 成功: {}, 失败: {}",
                asinIds.size(), submittedCount, failedCount);
        log.info("========================================");
        return submittedCount;
    }

    public boolean runForSingleAsin(Long asinId) {
        try {
            runForAsinAsync(asinId);
            return true;
        } catch (Exception e) {
            log.error("Failed to run for single ASIN: " + asinId, e);
            return false;
        }
    }
}

