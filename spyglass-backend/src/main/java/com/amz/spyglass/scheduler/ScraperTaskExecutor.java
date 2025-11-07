package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.model.ScrapeTaskModel;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.ScrapeTaskRepository;
import com.amz.spyglass.service.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 单个 ASIN 抓取执行器，独立 Bean 以确保 @Async/@Retryable 生效（跨 Bean 调用会走代理）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScraperTaskExecutor {

    private final AsinRepository asinRepository;
    private final ScraperService scraperService;
    private final ScrapeTaskRepository scrapeTaskRepository;
    private final com.amz.spyglass.repository.AsinHistoryRepository asinHistoryRepository;
    private final com.amz.spyglass.alert.AlertService alertService;

    @Value("${scraper.fixedDelayMs:14400000}")
    private long configuredDelay;

    @Async
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 3600000),
            include = {
                    org.springframework.web.client.ResourceAccessException.class,
                    java.io.IOException.class,
                    org.openqa.selenium.TimeoutException.class,
                    Exception.class
            }
    )
    public void executeForAsinAsync(Long asinId) throws Exception {
        int previousRetries = Optional.ofNullable(scrapeTaskRepository.findFirstByAsinIdOrderByCreatedAtDesc(asinId))
                .map(ScrapeTaskModel::getRetryCount)
                .orElse(0);
        log.info("[Task] 开始抓取 ASIN_ID={} (历史重试次数={})", asinId, previousRetries);

        ScrapeTaskModel task = new ScrapeTaskModel();
        task.setAsinId(asinId);
        task.setStatus(ScrapeTaskModel.TaskStatusConstants.RUNNING);
        task.setRunAt(Instant.now());
        scrapeTaskRepository.save(task);

        long execStart = System.currentTimeMillis();
        try {
            AsinModel asinModel = asinRepository.findById(asinId).orElseThrow(() -> new IllegalStateException("ASIN 不存在: " + asinId));
            String targetUrl = "https://www.amazon.com/dp/" + asinModel.getAsin();
            log.debug("[Task] 开始抓取 URL={} ASIN={} ", targetUrl, asinModel.getAsin());

            com.amz.spyglass.scraper.AsinSnapshotDTO snap = scraperService.fetchSnapshot(targetUrl);
            log.debug("[Task] 抓取完成 ASIN={} 字段摘要: title='{}', price={}, bsr={}, inventory={}, imageMd5={}, aplusMd5={}",
                    asinModel.getAsin(), truncate(snap.getTitle(), 60), snap.getPrice(), snap.getBsr(), snap.getInventory(), snap.getImageMd5(), snap.getAplusMd5());

            try { alertService.processAlerts(asinModel, snap); } catch (Exception e) { log.warn("[Task] 告警触发失败(预保存阶段) ASIN_ID={} msg={}", asinId, e.getMessage()); }

            persistHistorySnapshot(asinModel, snap);

            task.setStatus(ScrapeTaskModel.TaskStatusConstants.SUCCESS);
            task.setMessage("title=" + (snap.getTitle() == null ? "" : truncate(snap.getTitle(), 80)));
            task.markFinished();
            scrapeTaskRepository.save(task);
            log.info("[Task] 成功完成抓取 ASIN_ID={} 耗时={}ms", asinId, (System.currentTimeMillis() - execStart));

        } catch (Exception ex) {
            log.error("[Task] 抓取失败 ASIN_ID={} 当前尝试序号={} 错误信息={}", asinId, previousRetries + 1, ex.getMessage(), ex);
            task.setRetryCount(previousRetries + 1);
            if (task.getRetryCount() >= 3) {
                task.setStatus(ScrapeTaskModel.TaskStatusConstants.FAILED);
                task.setMessage("最终失败: " + ex.getMessage());
                log.warn("[Task] ASIN_ID={} 达到最大重试次数 ({} 次) 标记为 FAILED", asinId, task.getRetryCount());
            } else {
                task.setStatus(ScrapeTaskModel.TaskStatusConstants.PENDING);
                task.setMessage("等待重试 (attempt=" + task.getRetryCount() + "/3) cause=" + ex.getMessage());
                log.info("[Task] ASIN_ID={} 标记为 PENDING, 将由 Spring Retry 在 1 小时后重试", asinId);
            }
            task.markFinished();
            scrapeTaskRepository.save(task);
            throw ex;
        }
    }

    private void persistHistorySnapshot(AsinModel asinModel, com.amz.spyglass.scraper.AsinSnapshotDTO snap) {
        long start = System.currentTimeMillis();
        try {
            com.amz.spyglass.model.AsinHistoryModel h = new com.amz.spyglass.model.AsinHistoryModel();
            h.setAsin(asinModel);
            h.setTitle(snap.getTitle());
            h.setPrice(snap.getPrice());
            h.setBsr(snap.getBsr());
            h.setBsrCategory(snap.getBsrCategory());
            h.setBsrSubcategory(snap.getBsrSubcategory());
            h.setBsrSubcategoryRank(snap.getBsrSubcategoryRank());
            h.setInventory(snap.getInventory());
            h.setImageMd5(snap.getImageMd5());
            h.setTotalReviews(snap.getTotalReviews());
            h.setAvgRating(snap.getAvgRating());
            h.setBulletPoints(snap.getBulletPoints());
            h.setLatestNegativeReviewMd5(snap.getLatestNegativeReviewMd5());
            h.setSnapshotAt(snap.getSnapshotAt() == null ? Instant.now() : snap.getSnapshotAt());
            h.setAplusMd5(snap.getAplusMd5());

            asinHistoryRepository.save(h);
            log.info("[History] 保存快照成功 ASIN={} 字段: price={}, bsr=#{}, reviews={}, rating={}, inventory={}",
                    asinModel.getAsin(), h.getPrice(), h.getBsr(), h.getTotalReviews(), h.getAvgRating(), h.getInventory());
        } catch (Exception e) {
            log.warn("[History] 保存快照失败 ASIN={} 错误={}", asinModel.getAsin(), e.getMessage(), e);
        } finally {
            log.trace("[History] 保存操作耗时 {} ms", (System.currentTimeMillis() - start));
        }
    }

    private String truncate(String input, int maxLen) {
        if (input == null) return null;
        return input.length() <= maxLen ? input : input.substring(0, maxLen) + "...";
    }
}
