package com.amz.spyglass.controller;

import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.alert.PriceAlertRepository;
import com.amz.spyglass.repository.alert.ChangeAlertRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import com.amz.spyglass.alert.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Debug 调试控制器：用于人工快速插入基准快照与模拟字段变化，便于验证告警逻辑。
 */
@RestController
@RequestMapping("/debug")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final AsinRepository asinRepository;
    private final AsinHistoryRepository historyRepository;
    private final AlertService alertService;
    private final PriceAlertRepository priceAlertRepository;
    private final ChangeAlertRepository changeAlertRepository;

    public DebugController(AsinRepository asinRepository, AsinHistoryRepository historyRepository, AlertService alertService, PriceAlertRepository priceAlertRepository, ChangeAlertRepository changeAlertRepository) {
        this.asinRepository = asinRepository;
        this.historyRepository = historyRepository;
        this.alertService = alertService;
        this.priceAlertRepository = priceAlertRepository;
        this.changeAlertRepository = changeAlertRepository;
    }

    /**
     * 创建基准快照：为所有当前 asin 表中的记录插入一条历史记录（若已存在至少一条则跳过该 ASIN）。
     */
    @RequestMapping(value = "/create-baseline", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> createBaseline() {
        List<AsinModel> all = asinRepository.findAll();
        int created = 0;
        log.info("[Debug] create-baseline 请求开始, ASIN 总数={} ", all.size());
        for (AsinModel asin : all) {
            var existing = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asin.getId());
            if (!existing.isEmpty()) continue; // 已有历史则跳过
            AsinHistoryModel h = new AsinHistoryModel();
            h.setAsin(asin);
            h.setPrice(new BigDecimal("50.00"));
            h.setTitle("Baseline Title for " + asin.getAsin());
            h.setImageMd5("img_md5_baseline");
            h.setAplusMd5("aplus_md5_baseline");
            h.setBulletPoints("Bullet A\nBullet B");
            h.setLatestNegativeReviewMd5("neg_rev_md5_baseline");
            h.setSnapshotAt(Instant.now());
            historyRepository.save(h);
            created++;
        }
        log.info("[Debug] create-baseline 完成, 新增历史条数={}", created);
        return ResponseEntity.ok("Baseline created for ASIN count: " + created);
    }

    /**
     * 模拟变更：为每个 ASIN 构造一个变化后的快照并调用告警服务。
     * 变化内容：价格 +1.23，标题尾部追加标记，主图 / A+ / 五点 / 差评 MD5 都改成 new_* 前缀。
     */
    @RequestMapping(value = "/simulate-changes", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> simulateChanges() {
        List<AsinModel> all = asinRepository.findAll();
        int alerted = 0;
        int persisted = 0;
        log.info("[Debug] simulate-changes 请求开始, ASIN 总数={}", all.size());
        for (AsinModel asin : all) {
            var existing = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asin.getId());
            if (existing.isEmpty()) continue; // 无基准则跳过
            AsinHistoryModel last = existing.getFirst();
            AsinSnapshotDTO snap = new AsinSnapshotDTO();
            snap.setPrice(last.getPrice() == null ? new BigDecimal("51.23") : last.getPrice().add(new BigDecimal("1.23")));
            snap.setTitle((last.getTitle() == null ? "Title" : last.getTitle()) + " [CHANGED]");
            snap.setImageMd5("new_" + last.getImageMd5());
            snap.setAplusMd5("new_" + last.getAplusMd5());
            snap.setBulletPoints((last.getBulletPoints() == null ? "BP" : last.getBulletPoints()) + "\nAdded line");
            snap.setLatestNegativeReviewMd5("new_" + last.getLatestNegativeReviewMd5());
            snap.setInventory(5); // 模拟低库存
            alertService.processAlerts(asin, snap);
            alerted++;
            // 同时持久化这次模拟快照，形成新的历史点
            try {
                AsinHistoryModel h = new AsinHistoryModel();
                h.setAsin(asin);
                h.setPrice(snap.getPrice());
                h.setTitle(snap.getTitle());
                h.setImageMd5(snap.getImageMd5());
                h.setAplusMd5(snap.getAplusMd5());
                h.setBulletPoints(snap.getBulletPoints());
                h.setLatestNegativeReviewMd5(snap.getLatestNegativeReviewMd5());
                h.setSnapshotAt(Instant.now());
                historyRepository.save(h);
                persisted++;
            } catch (Exception e) {
                log.warn("[Debug] 模拟快照保存失败 asin={} msg={}", asin.getAsin(), e.getMessage());
            }
        }
        log.info("[Debug] simulate-changes 完成, 触发告警条数={}, 保存历史条数={}", alerted, persisted);
        return ResponseEntity.ok("Simulated changes for ASIN count: " + alerted + ", persisted snapshots=" + persisted);
    }

    /**
     * 返回当前告警表中的数据条数，便于无需直接连库即可快速观察。
     */
    @RequestMapping(value = "/alert-stats", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> alertStats() {
        long priceCount = priceAlertRepository.count();
        long changeCount = changeAlertRepository.count();
        log.info("[Debug] alert-stats 查询: price_alert={}, change_alert={}", priceCount, changeCount);
        return ResponseEntity.ok("price_alert=" + priceCount + ", change_alert=" + changeCount);
    }

    /**
     * 重置所有 ASIN 的历史快照（清空 asin_history 表），用于再次构造干净基准。
     * 仅用于本地/调试，请勿在生产暴露；这里简单实现为直接删除。
     */
    @RequestMapping(value = "/reset-history", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> resetHistory() {
        try {
            long countBefore = historyRepository.count();
            historyRepository.deleteAll();
            log.warn("[Debug] reset-history 执行，删除历史条数={}", countBefore);
            return ResponseEntity.ok("deleted=" + countBefore);
        } catch (Exception e) {
            log.error("[Debug] reset-history 失败 msg={}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("error=" + e.getMessage());
        }
    }

    /**
     * 强制构造一次差异快照：不依赖真实抓取。用于验证告警链条。
     * 流程：
     *  1. 选取第一个 ASIN（若无返回提示）
     *  2. 读取其最近历史（若无历史返回提示）
     *  3. 人工构造变化后的 DTO（价格-5，标题追加 [FORCED], 主图MD5 前缀 forced_, A+ 前缀 forced_，五点追加一行，差评MD5 forced_new）
     *  4. 调用 alertService.processAlerts 触发告警
     */
    @RequestMapping(value = "/force-change", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> forceChange() {
        var asinOpt = asinRepository.findAll().stream().findFirst();
        if (asinOpt.isEmpty()) {
            return ResponseEntity.ok("no asin row");
        }
        AsinModel asin = asinOpt.get();
        var histories = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asin.getId());
        if (histories.isEmpty()) {
            return ResponseEntity.ok("no history for asin=" + asin.getAsin());
        }
        AsinHistoryModel last = histories.getFirst();
        AsinSnapshotDTO snap = new AsinSnapshotDTO();
        snap.setPrice(last.getPrice() == null ? new BigDecimal("19.99") : last.getPrice().subtract(new BigDecimal("5.00")));
        snap.setTitle((last.getTitle() == null ? "Title" : last.getTitle()) + " [FORCED]");
        snap.setImageMd5("forced_" + last.getImageMd5());
        snap.setAplusMd5("forced_" + last.getAplusMd5());
        snap.setBulletPoints((last.getBulletPoints() == null ? "BP" : last.getBulletPoints()) + "\nForced line");
        snap.setLatestNegativeReviewMd5("forced_" + last.getLatestNegativeReviewMd5());
        snap.setInventory(1); // 触发库存告警
        alertService.processAlerts(asin, snap);
        long priceCount = priceAlertRepository.count();
        long changeCount = changeAlertRepository.count();
        return ResponseEntity.ok("forced change submitted asin=" + asin.getAsin() + " price_alert=" + priceCount + " change_alert=" + changeCount);
    }
}
