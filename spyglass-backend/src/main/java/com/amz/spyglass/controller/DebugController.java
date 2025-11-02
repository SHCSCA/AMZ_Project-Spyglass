package com.amz.spyglass.controller;

import com.amz.spyglass.scheduler.ScraperScheduler;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.model.AsinHistoryModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * DebugController: 仅用于本地/验收环境手动触发抓取与制造告警场景。
 * 不建议在生产环境开启（可后续通过 profile 或安全策略屏蔽）。
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final ScraperScheduler scraperScheduler;
    private final AsinRepository asinRepository;
    private final AsinHistoryRepository historyRepository;

    /**
     * 手动触发单个 ASIN 抓取（异步），返回是否提交成功。
     */
    @PostMapping("/scrape/{asinId}")
    public ResponseEntity<String> scrapeSingle(@PathVariable Long asinId) {
        boolean submitted = scraperScheduler.runForSingleAsin(asinId);
        log.info("[Debug] 手动提交抓取 asinId={} submitted={}", asinId, submitted);
        return submitted ? ResponseEntity.ok("submitted") : ResponseEntity.badRequest().body("asin not found or submit failed");
    }

    /**
     * 修改最新一条历史记录的标题（强制制造字段变化场景）。
     */
    @PostMapping("/force-title/{asinId}")
    public ResponseEntity<String> forceTitleChange(@PathVariable Long asinId, @RequestParam(defaultValue = "FORCED-OLD-TITLE") String newOldTitle) {
        // 查询最新一条历史
        Optional<AsinHistoryModel> latestOpt = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asinId).stream().findFirst();
        if (latestOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("no history to mutate");
        }
        AsinHistoryModel h = latestOpt.get();
        String original = h.getTitle();
        h.setTitle(newOldTitle + " @" + Instant.now());
        historyRepository.save(h);
        log.info("[Debug] 强制修改历史标题 asinId={} old='{}' new='{}'", asinId, original, h.getTitle());
        return ResponseEntity.ok("mutated from '" + original + "' to '" + h.getTitle() + "'");
    }

    /**
     * 修改最新一条历史记录的价格（制造价格变化基础）。
     */
    @PostMapping("/force-price/{asinId}")
    public ResponseEntity<String> forcePriceChange(@PathVariable Long asinId, @RequestParam(defaultValue = "49.99") Double baselinePrice) {
        Optional<AsinHistoryModel> latestOpt = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asinId).stream().findFirst();
        if (latestOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("no history to mutate");
        }
        AsinHistoryModel h = latestOpt.get();
        Double oldVal = h.getPrice() == null ? null : h.getPrice().doubleValue();
        h.setPrice(BigDecimal.valueOf(baselinePrice));
        historyRepository.save(h);
        log.info("[Debug] 强制修改历史价格 asinId={} oldPrice={} newPrice={} baseline={} ", asinId, oldVal, h.getPrice(), baselinePrice);
        return ResponseEntity.ok("price mutated from " + oldVal + " to " + baselinePrice);
    }

    /**
     * 调试：直接查看指定 ASIN 是否存在（绕过分页接口 500 问题）。
     */
    @GetMapping("/asin/{asinId}")
    public ResponseEntity<String> checkAsin(@PathVariable Long asinId) {
        return asinRepository.findById(asinId)
                .map(a -> ResponseEntity.ok("FOUND asin=" + a.getAsin() + ", id=" + a.getId()))
                .orElseGet(() -> ResponseEntity.badRequest().body("NOT_FOUND id=" + asinId));
    }

    /**
     * 调试：统计当前 ASIN 总数。
     */
    @GetMapping("/asin-count")
    public ResponseEntity<String> asinCount() {
        long count = asinRepository.count();
        return ResponseEntity.ok("asin_count=" + count);
    }
}
