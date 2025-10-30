package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.AsinModel;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import com.amz.spyglass.model.ScrapeTaskModel;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.ScrapeTaskRepository;
import com.amz.spyglass.service.ScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Instant;

/**
 * 抓取任务调度器
 * 负责定时触发所有 ASIN 的抓取任务，包括以下功能：
 * 1. 每隔固定时间（默认4小时）批量抓取所有 ASIN
 * 2. 支持单个 ASIN 的异步抓取
 * 3. 记录抓取任务状态和结果
 * 4. 保存抓取快照到历史记录
 * 
 * @author AI
 * @version 1.0.0
 * @since 2025-10-30
 */
@Component
@Profile("!test") // 测试环境下不启用调度器
@EnableRetry // 启用重试机制
public class ScraperScheduler {

    private final Logger logger = LoggerFactory.getLogger(ScraperScheduler.class);
    
    /**
     * ASIN 仓储服务，用于获取待抓取的 ASIN 列表
     */
    private final AsinRepository asinRepository;
    
    /**
     * 抓取服务，执行实际的页面抓取逻辑
     */
    private final ScraperService scraperService;
    
    /**
     * 抓取任务仓储服务，用于记录任务状态
     */
    private final ScrapeTaskRepository scrapeTaskRepository;
    
    /**
     * ASIN 历史记录仓储服务，用于保存抓取快照
     */
    private final com.amz.spyglass.repository.AsinHistoryRepository asinHistoryRepository;

    public ScraperScheduler(AsinRepository asinRepository, ScraperService scraperService, ScrapeTaskRepository scrapeTaskRepository, com.amz.spyglass.repository.AsinHistoryRepository asinHistoryRepository) {
        this.asinRepository = asinRepository;
        this.scraperService = scraperService;
        this.scrapeTaskRepository = scrapeTaskRepository;
        this.asinHistoryRepository = asinHistoryRepository;
    }

    /**
     * 批量执行所有 ASIN 的抓取任务
     * 默认每4小时执行一次（14400000毫秒）
     * 可通过配置项 scraper.fixedDelayMs 覆盖默认间隔
     */
    @Scheduled(fixedDelayString = "${scraper.fixedDelayMs:14400000}")
    public void runAll() {
        logger.info("开始执行批量抓取任务...");
        for (AsinModel asin : asinRepository.findAll()) {
            try {
                runForAsinAsync(asin.getId());
            } catch (Exception ex) {
                logger.error("Failed to schedule task for ASIN {}: {}", asin.getAsin(), ex.getMessage());
            }
        }
        logger.info("批量抓取任务已全部提交到异步队列");
    }

    /**
     * 异步执行单个 ASIN 的抓取任务
     * 任务执行过程：
     * 1. 创建任务记录并设置为运行中
     * 2. 调用抓取服务获取页面数据（支持重试）
     * 3. 保存抓取快照到历史记录
     * 4. 更新任务状态为成功或失败
     *
     * @param asinId ASIN记录的ID
     */
    @Async
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 3600000), // 1小时后重试
        include = { 
            org.springframework.web.client.ResourceAccessException.class,
            java.io.IOException.class,
            org.openqa.selenium.TimeoutException.class,
            Exception.class
        }
    )
    public void runForAsinAsync(Long asinId) throws Exception {
        logger.info("开始异步抓取任务, ASIN ID: {}", asinId);
        ScrapeTaskModel task = new ScrapeTaskModel();
        task.setAsinId(asinId);
        task.setStatus(ScrapeTaskModel.TaskStatus.RUNNING);
        task.setRunAt(Instant.now());
        scrapeTaskRepository.save(task);

        try {
            // placeholder: use scraperService to fetch title (real logic should fetch price, bsr, etc.)
            AsinModel a = asinRepository.findById(asinId).orElseThrow();
            // 调用 ScraperService 获取完整的页面快照（包含 price/bsr/inventory/imageMd5/aplusMd5）
            com.amz.spyglass.scraper.AsinSnapshotDTO snap = scraperService.fetchSnapshot("https://www.amazon.com/dp/" + a.getAsin());

            task.setStatus(ScrapeTaskModel.TaskStatus.SUCCESS);
            task.setMessage("title=" + (snap.getTitle() == null ? "" : snap.getTitle()));
            task.setRunAt(Instant.now());
            scrapeTaskRepository.save(task);

            // 保存历史快照到 AsinHistory 实体
            try {
                com.amz.spyglass.model.AsinHistoryModel h = new com.amz.spyglass.model.AsinHistoryModel();
                h.setAsin(a);
                h.setTitle(snap.getTitle());
                h.setPrice(snap.getPrice());
                h.setBsr(snap.getBsr());
                h.setInventory(snap.getInventory());
                h.setImageMd5(snap.getImageMd5());
                h.setAplusMd5(snap.getAplusMd5());
                h.setBulletPoints(snap.getBulletPoints());
                h.setSnapshotAt(snap.getSnapshotAt() == null ? Instant.now() : snap.getSnapshotAt());
                asinHistoryRepository.save(h);
            } catch (Exception e) {
                logger.warn("failed to save asin history for {}: {}", asinId, e.getMessage());
            }
        } catch (Exception ex) {
            logger.error("scrape failed for asin {} (attempt {})", asinId, task.getRetryCount() + 1, ex);
            task.setRetryCount(task.getRetryCount() + 1);
            
            // 如果重试次数达到上限，标记为失败；否则标记为待重试
            if (task.getRetryCount() >= 3) {
                task.setStatus(ScrapeTaskModel.TaskStatus.FAILED);
                task.setMessage(ex.getMessage() + " (after " + task.getRetryCount() + " retries)");
            } else {
                task.setStatus(ScrapeTaskModel.TaskStatus.PENDING);
                task.setMessage("将在1小时后重试 (attempt " + task.getRetryCount() + "/3)");
                task.setRunAt(Instant.now().plusSeconds(3600)); // 1小时后重试
            }
            scrapeTaskRepository.save(task);
            
            // 重新抛出异常以触发 Spring Retry
            throw ex;
        }
    }
}
