package com.amz.spyglass.controller;

import com.amz.spyglass.scheduler.ScraperScheduler;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.model.AsinHistoryModel;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
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
    private final ApplicationContext applicationContext; // 用于检测 OpenAPI Bean 与映射

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

    /**
     * 手动触发批量调度（runAll），返回当前 ASIN 数量与立即触发状态。
     * 注意：runAll 内部使用异步提交，返回不代表全部任务已完成。
     */
    @GetMapping("/scheduler/run-batch")
    public ResponseEntity<java.util.Map<String, Object>> triggerBatchRun() {
        var asins = asinRepository.findAll();
        int count = asins.size();
        log.info("[Debug] 手动触发批量调度，当前 ASIN 总数={}", count);
        try {
            scraperScheduler.runAll();
        } catch (Exception e) {
            log.error("[Debug] 手动触发批量调度异常: {}", e.getMessage(), e);
            java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("submitted", false);
            err.put("asinCount", count);
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
        java.util.Map<String, Object> ok = new java.util.LinkedHashMap<>();
        ok.put("submitted", true);
        ok.put("asinCount", count);
        ok.put("note", "Tasks submitted asynchronously; check scheduler logs for completion.");
        return ResponseEntity.ok(ok);
    }

    /**
     * 调试：列出当前全部 ASIN 基本信息 (id, asin, site, nickname)。
     * 仅用于排查调度时数量不一致的问题。
     */
    @GetMapping("/asin/list")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> asinList() {
        var list = asinRepository.findAll();
        var resp = list.stream().map(a -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("asin", a.getAsin());
            m.put("site", a.getSite());
            m.put("nickname", a.getNickname());
            return m;
        }).toList();
        return ResponseEntity.ok(resp);
    }

    /**
     * 调试：检测 OpenAPI Bean 是否存在以及 /v3/api-docs 映射是否注册。
     * 用于生产环境 Swagger 500 排查。
     */
    @GetMapping("/openapi/status")
    public ResponseEntity<java.util.Map<String, Object>> openApiStatus() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        boolean beanExists = applicationContext.getBeanNamesForType(OpenAPI.class).length > 0;
        result.put("openApiBeanExists", beanExists);
        // 检测是否已注册 /v3/api-docs
        boolean apiDocsMapped = false;
        try {
            RequestMappingHandlerMapping mapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
            apiDocsMapped = mapping.getHandlerMethods().keySet().stream()
                .anyMatch(info -> info.getPatternsCondition() != null &&
                    info.getPatternsCondition().getPatterns().stream().anyMatch(p -> p.equals("/v3/api-docs")));
        } catch (Exception e) {
            log.warn("[Debug] 检测 /v3/api-docs 映射异常: {}", e.getMessage());
        }
        result.put("apiDocsMapped", apiDocsMapped);
        // 读取 springdoc 版本（若存在）
        String springdocVersion = null;
        try {
            Package pkg = Class.forName("org.springdoc.core.SpringDocConfigProperties").getPackage();
            if (pkg != null) {
                springdocVersion = pkg.getImplementationVersion();
            }
        } catch (ClassNotFoundException ignored) {}
        result.put("springdocVersion", springdocVersion);
        return ResponseEntity.ok(result);
    }

    /**
     * 列出所有包含 "springdoc" 关键字的 Bean 名称与类型，辅助生产排查未注册 OpenApiResource 的原因。
     */
    @GetMapping("/openapi/beans")
    public ResponseEntity<java.util.List<java.util.Map<String, String>>> listSpringdocBeans() {
        String keyword = "springdoc";
    String[] names = applicationContext.getBeanDefinitionNames();
    java.util.List<java.util.Map<String, String>> matched = new java.util.ArrayList<>();
    for (String n : names) {
            if (n.toLowerCase().contains(keyword)) {
                try {
                    Object bean = applicationContext.getBean(n);
                    java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
                    m.put("name", n);
                    m.put("type", bean.getClass().getName());
                    matched.add(m);
                } catch (Exception e) {
                    java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
                    m.put("name", n);
                    m.put("error", e.getMessage());
                    matched.add(m);
                }
            }
        }
        return ResponseEntity.ok(matched);
    }
}
