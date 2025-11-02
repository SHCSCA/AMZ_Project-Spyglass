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
}
