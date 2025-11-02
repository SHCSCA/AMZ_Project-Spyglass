package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.AsinModel;
import java.util.Optional;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import com.amz.spyglass.model.ScrapeTaskModel;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.ScrapeTaskRepository;
import com.amz.spyglass.service.ScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Instant;

/**
 * 抓取任务调度器 (Scheduler)
 * 职责：周期性为“所有已监控的 ASIN”提交抓取任务，并提供“单 ASIN 异步抓取 + 自动重试”能力。
 * 关键特点：
 *  - 定时批处理：使用 @Scheduled 以固定延迟方式轮询（默认 4 小时）
 *  - 异步执行：单个抓取任务使用 @Async 防止阻塞调度线程
 *  - 重试机制：@Retryable 支持网络/IO/超时类异常的自动重试（最多 3 次，间隔 1 小时）
 *  - 任务记录：ScrapeTaskRepository 记录每一次的任务状态（运行中/成功/失败/待重试）
 *  - 历史快照：成功后写入 AsinHistoryRepository，形成时间序列数据
 * 注意：当前未包含“新旧快照对比并触发告警”的逻辑，可在成功保存后扩展。
 */
@Component
@Profile("!test && !mysqltest") // 在 test 与 mysqltest 集成测试 profile 下不加载，避免初始与定时调度干扰计数
@EnableRetry // 启用 Spring Retry，配合 @Retryable 注解使用
@Slf4j
@RequiredArgsConstructor
public class ScraperScheduler {

    // ASIN 维护仓库：提供所有需要抓取的 ASIN 模型列表
    private final AsinRepository asinRepository;
    // 抓取服务：内部封装 Jsoup + Selenium 抓取与回退策略
    private final ScraperService scraperService;
    // 抓取任务仓库：记录抓取执行状态与重试信息
    private final ScrapeTaskRepository scrapeTaskRepository;
    // 历史快照仓库：保存每次抓取后的数据点（用于后续对比与展示）
    private final com.amz.spyglass.repository.AsinHistoryRepository asinHistoryRepository;
    private final com.amz.spyglass.alert.AlertService alertService;
    @Value("${scraper.fixedDelayMs:14400000}")
    private long configuredDelay;

        /**
     * 批量调度所有 ASIN 的抓取任务。
     * fixedDelayString：上一次执行"结束"到下一次"开始"之间的间隔（默认 14400000 ms ≈ 4 小时）。
     * 
     * 流程：
     *  1. 读取数据库中全部 ASIN
     *  2. 遍历并调用 runForAsinAsync 提交异步任务（不阻塞本方法）
     *  3. 记录调度日志方便运维排查
     * 
     * 失败策略：
     *  - 单个 ASIN 提交失败仅记录错误，不影响其他 ASIN 的调度
     *  - 每个 ASIN 的抓取在独立事务中执行（REQUIRES_NEW），互不影响
     *  - 支持自动重试机制（@Retryable 3次，间隔1小时）
     * 
     * 多 ASIN 保障：
     *  - 异步执行：每个 ASIN 独立线程，互不阻塞
     *  - 事务隔离：一个 ASIN 失败不会回滚其他 ASIN 的数据
     *  - 异常隔离：try-catch 确保单个失败不影响整体调度
     */
    @Scheduled(fixedDelayString = "${scraper.fixedDelayMs:14400000}", initialDelayString = "${scraper.initialDelayMs:0}")
    public void runAll() {
        long start = System.currentTimeMillis();
    log.info("========================================");
    log.info("[Scheduler] 开始批量调度抓取任务 (fixedDelay={}ms)...", getConfiguredDelay());
        java.util.List<AsinModel> all = asinRepository.findAll();
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

    /**
     * 异步执行单个 ASIN 的抓取任务。
     * 每个 ASIN 的抓取在独立事务中执行，互不影响。
     * 
     * 执行细节：
     *  1. 创建 ScrapeTaskModel 记录（状态=RUNNING）
     *  2. 根据 ASIN 拼接产品详情页 URL 调用 scraperService.fetchSnapshot
     *  3. 任务成功：更新状态=SUCCESS，记录关键信息，写入历史快照（包含所有14个字段）
     *  4. 任务失败：更新 retryCount；根据次数决定状态（PENDING 或 FAILED），并抛出异常以触发 Spring Retry
     * 
     * 重试策略：@Retryable 最多尝试 3 次，每次失败后等待 1 小时（Backoff）再自动重试。
     * 并发与线程：@Async 注解让方法在 TaskExecutor（默认 SimpleAsyncTaskExecutor）中运行，避免阻塞调度线程。
     * 事务隔离：@Transactional 确保每个 ASIN 的数据操作在独立事务中，一个失败不影响其他 ASIN。
     * 
     * @param asinId ASIN 主键 ID
     * @throws Exception 让 Spring Retry 感知并执行重试
     */
    @Async
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
        rollbackFor = Exception.class
    )
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 3600000), // 第一次失败后 1 小时重试；可根据需要改为指数退避
        include = {
            org.springframework.web.client.ResourceAccessException.class,
            java.io.IOException.class,
            org.openqa.selenium.TimeoutException.class,
            Exception.class // 兜底：其他未分类的异常
        }
    )
    public void runForAsinAsync(Long asinId) throws Exception {
        // 计算当前的“已重试次数”用于日志输出（读取最近一条任务记录）
        int previousRetries = Optional.ofNullable(scrapeTaskRepository.findFirstByAsinIdOrderByCreatedAtDesc(asinId))
            .map(ScrapeTaskModel::getRetryCount)
            .orElse(0);
    log.info("[Task] 开始抓取 ASIN_ID={} (历史重试次数={})", asinId, previousRetries);

        // 初始化任务记录
        ScrapeTaskModel task = new ScrapeTaskModel();
        task.setAsinId(asinId);
    task.setStatus(ScrapeTaskModel.TaskStatusConstants.RUNNING);
        task.setRunAt(Instant.now());
        scrapeTaskRepository.save(task);

        long execStart = System.currentTimeMillis();
        try {
            // 加载 ASIN 模型（若不存在抛出异常交由重试处理）
            AsinModel asinModel = asinRepository.findById(asinId).orElseThrow(() -> new IllegalStateException("ASIN 不存在: " + asinId));
            String targetUrl = "https://www.amazon.com/dp/" + asinModel.getAsin();
            log.debug("[Task] 开始抓取 URL={} ASIN={} ", targetUrl, asinModel.getAsin());

            // 调用抓取服务（内部包含 Jsoup + Selenium 回退策略），返回聚合的快照 DTO
            com.amz.spyglass.scraper.AsinSnapshotDTO snap = scraperService.fetchSnapshot(targetUrl);
            log.debug("[Task] 抓取完成 ASIN={} 字段摘要: title='{}', price={}, bsr={}, inventory={}, imageMd5={}, aplusMd5={}",
                asinModel.getAsin(), truncate(snap.getTitle(), 60), snap.getPrice(), snap.getBsr(), snap.getInventory(), snap.getImageMd5(), snap.getAplusMd5());

            // 触发告警：故意在保存新快照之前执行，对比当前最新一条历史与本次抓取结果
            try { alertService.processAlerts(asinModel, snap); } catch (Exception e) { log.warn("[Task] 告警触发失败(预保存阶段) ASIN_ID={} msg={}", asinId, e.getMessage()); }

            // 写入新的历史快照（作为下一轮对比的旧值）
            persistHistorySnapshot(asinModel, snap);

            // 标记任务成功（放在最后，表示全流程已结束）
            task.setStatus(ScrapeTaskModel.TaskStatusConstants.SUCCESS);
            task.setMessage("title=" + (snap.getTitle() == null ? "" : truncate(snap.getTitle(), 80)));
            task.markFinished();
            scrapeTaskRepository.save(task);
            log.info("[Task] 成功完成抓取 ASIN_ID={} 耗时={}ms", asinId, (System.currentTimeMillis() - execStart));

        } catch (Exception ex) {
            // 发生异常，更新任务重试计数与状态
            log.error("[Task] 抓取失败 ASIN_ID={} 当前尝试序号={} 错误信息={}", asinId, previousRetries + 1, ex.getMessage(), ex);
            task.setRetryCount(previousRetries + 1);
            // 达到最大次数 -> 失败；否则置为 PENDING 由 Spring Retry 再次调用（下次进入方法会新增 RUNNING 记录）
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
            // 抛出异常交给 Spring Retry 处理（决定是否再次调用本方法）
            throw ex;
        }
    }

    /**
     * 持久化历史快照辅助方法：将抓取结果 DTO 映射并保存为数据库实体。
     * 保存所有14个字段，确保数据完整性。
     * 不抛出运行时异常（内部捕获），避免影响主流程；仅在失败时记录警告日志。
     * 
     * 字段清单（14个）：
     * 必需字段(8): title, price, bsr, imageMd5, totalReviews, avgRating, bulletPoints, snapshotAt
     * 可选字段(6): bsrCategory, bsrSubcategory, bsrSubcategoryRank, inventory, aplusMd5, latestNegativeReviewMd5
     */
    private void persistHistorySnapshot(AsinModel asinModel, com.amz.spyglass.scraper.AsinSnapshotDTO snap) {
        long start = System.currentTimeMillis();
        try {
            com.amz.spyglass.model.AsinHistoryModel h = new com.amz.spyglass.model.AsinHistoryModel();
            h.setAsin(asinModel);
            
            // 必需字段
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
            h.setTotalReviews(snap.getTotalReviews());
            h.setAvgRating(snap.getAvgRating());
            // 新增：保存最新差评 MD5 用于后续告警对比
            h.setLatestNegativeReviewMd5(snap.getLatestNegativeReviewMd5());
            h.setSnapshotAt(snap.getSnapshotAt() == null ? Instant.now() : snap.getSnapshotAt());
            
            // 可选字段（BSR 分类信息）
            h.setBsrCategory(snap.getBsrCategory());
            h.setBsrSubcategory(snap.getBsrSubcategory());
            h.setBsrSubcategoryRank(snap.getBsrSubcategoryRank());
            
            // 可选字段（其他）
            h.setInventory(snap.getInventory());
            h.setAplusMd5(snap.getAplusMd5());
            h.setLatestNegativeReviewMd5(snap.getLatestNegativeReviewMd5());
            
            asinHistoryRepository.save(h);
            log.info("[History] 保存快照成功 ASIN={} 字段: price={}, bsr=#{}, reviews={}, rating={}, inventory={}", 
                asinModel.getAsin(), h.getPrice(), h.getBsr(), h.getTotalReviews(), h.getAvgRating(), h.getInventory());
        } catch (Exception e) {
            log.warn("[History] 保存快照失败 ASIN={} 错误={}", asinModel.getAsin(), e.getMessage(), e);
        } finally {
            log.trace("[History] 保存操作耗时 {} ms", (System.currentTimeMillis() - start));
        }
    }

    /**
     * 获取当前配置的调度延迟（用于日志展示）
     */
    private long getConfiguredDelay() {
        return configuredDelay;
    }

    /**
     * 工具：安全截断字符串（避免日志过长）
     */
    private String truncate(String input, int maxLen) {
        if (input == null) return null;
        return input.length() <= maxLen ? input : input.substring(0, maxLen) + "...";
    }


    /**
     * 手动触发指定 ASIN 列表的抓取任务
     * 用于测试、手动补抓或按需抓取场景
     * 
     * @param asinIds ASIN ID 列表
     * @return 提交成功的数量
     */
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

    /**
     * 手动触发单个 ASIN 的抓取任务
     * 
     * @param asinId ASIN ID
     * @return 是否提交成功
     */
    public boolean runForSingleAsin(Long asinId) {
        return runForSpecificAsins(java.util.Collections.singletonList(asinId)) > 0;
    }
}
