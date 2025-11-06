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
    private final javax.sql.DataSource dataSource; // 用于执行原生 SQL 查询表结构

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
     * 修改最新一条历史记录的 bullet points（测试长文本字段）。
     */
    @PostMapping("/force-bulletpoints/{asinId}")
    public ResponseEntity<String> forceBulletPointsChange(@PathVariable Long asinId, @RequestParam(defaultValue = "SHORT_TEST_BULLETPOINTS") String newBulletPoints) {
        Optional<AsinHistoryModel> latestOpt = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asinId).stream().findFirst();
        if (latestOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("no history to mutate");
        }
        AsinHistoryModel h = latestOpt.get();
        String originalBp = h.getBulletPoints();
        int oldLen = originalBp == null ? 0 : originalBp.length();
        h.setBulletPoints(newBulletPoints);
        historyRepository.save(h);
        int newLen = newBulletPoints.length();
        log.info("[Debug] 强制修改历史bulletPoints asinId={} oldLength={} newLength={}", asinId, oldLen, newLen);
        return ResponseEntity.ok("bulletPoints mutated from length=" + oldLen + " to length=" + newLen);
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
            apiDocsMapped = false;
            for (var info : mapping.getHandlerMethods().keySet()) {
                var patternsCondition = info.getPatternsCondition();
                if (patternsCondition == null) continue;
                for (String p : patternsCondition.getPatterns()) {
                    if ("/v3/api-docs".equals(p)) { apiDocsMapped = true; break; }
                }
                if (apiDocsMapped) break;
            }
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

    /**
     * 查询 change_alert 表结构和数据统计，用于诊断字段长度问题
     */
    @GetMapping("/change-alert/info")
    public ResponseEntity<java.util.Map<String, Object>> getChangeAlertInfo() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            
            // 1. 查询表结构
            String structureSql = "SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                                "FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'change_alert' " +
                                "ORDER BY ORDINAL_POSITION";
            
            java.util.List<java.util.Map<String, Object>> columns = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(structureSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    columns.add(col);
                }
            }
            result.put("columns", columns);
            
            // 2. 统计记录数
            String countSql = "SELECT COUNT(*) as total FROM change_alert";
            try (java.sql.ResultSet rs = stmt.executeQuery(countSql)) {
                if (rs.next()) {
                    result.put("totalRecords", rs.getInt("total"));
                }
            }
            
            // 3. 按类型分组统计
            String groupSql = "SELECT alert_type, COUNT(*) as count FROM change_alert GROUP BY alert_type";
            java.util.Map<String, Integer> byType = new java.util.LinkedHashMap<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(groupSql)) {
                while (rs.next()) {
                    byType.put(rs.getString("alert_type"), rs.getInt("count"));
                }
            }
            result.put("countByType", byType);
            
            // 4. 最新5条记录（包含字段长度）
            String recentSql = "SELECT id, asin_id, alert_type, " +
                             "LENGTH(old_value) as old_len, LENGTH(new_value) as new_len, " +
                             "LEFT(old_value, 50) as old_preview, LEFT(new_value, 50) as new_preview, " +
                             "alert_at FROM change_alert ORDER BY alert_at DESC LIMIT 5";
            java.util.List<java.util.Map<String, Object>> recent = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(recentSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> rec = new java.util.LinkedHashMap<>();
                    rec.put("id", rs.getLong("id"));
                    rec.put("asinId", rs.getLong("asin_id"));
                    rec.put("alertType", rs.getString("alert_type"));
                    rec.put("oldLength", rs.getInt("old_len"));
                    rec.put("newLength", rs.getInt("new_len"));
                    rec.put("oldPreview", rs.getString("old_preview"));
                    rec.put("newPreview", rs.getString("new_preview"));
                    rec.put("alertAt", rs.getTimestamp("alert_at"));
                    recent.add(rec);
                }
            }
            result.put("recentRecords", recent);
            
            result.put("success", true);
            
        } catch (Exception e) {
            log.error("[Debug] 查询 change_alert 表信息失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 执行 change_alert 表字段类型修复
     * ⚠️ 警告：此操作会修改数据库表结构，仅在确认需要修复时调用
     */
    @PostMapping("/change-alert/fix-field-length")
    public ResponseEntity<java.util.Map<String, Object>> fixChangeAlertFieldLength() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            
            log.info("[Debug] 开始修复 change_alert 表字段长度...");
            
            // 1. 记录修复前的字段类型
            String beforeSql = "SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH " +
                             "FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'change_alert' " +
                             "AND COLUMN_NAME IN ('old_value', 'new_value')";
            
            java.util.List<java.util.Map<String, Object>> beforeFix = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(beforeSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    beforeFix.add(col);
                }
            }
            result.put("beforeFix", beforeFix);
            
            // 2. 执行 ALTER TABLE 修改字段类型
            String alterOldValue = "ALTER TABLE change_alert MODIFY COLUMN old_value TEXT COMMENT '变更前的值'";
            String alterNewValue = "ALTER TABLE change_alert MODIFY COLUMN new_value TEXT COMMENT '变更后的值'";
            
            log.info("[Debug] 执行 SQL: {}", alterOldValue);
            stmt.executeUpdate(alterOldValue);
            
            log.info("[Debug] 执行 SQL: {}", alterNewValue);
            stmt.executeUpdate(alterNewValue);
            
            // 3. 验证修复结果
            java.util.List<java.util.Map<String, Object>> afterFix = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(beforeSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    afterFix.add(col);
                }
            }
            result.put("afterFix", afterFix);
            
            result.put("success", true);
            result.put("message", "字段类型修复成功：old_value 和 new_value 已修改为 TEXT 类型");
            log.info("[Debug] change_alert 表字段长度修复完成");
            
        } catch (Exception e) {
            log.error("[Debug] 修复 change_alert 表字段长度失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 修复 alert_log 表的 old_value 和 new_value 字段长度(TINYTEXT -> TEXT)
     */
    @PostMapping("/alert-log/fix-field-length")
    public ResponseEntity<java.util.Map<String, Object>> fixAlertLogFieldLength() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            
            // 1. 查询修复前的字段类型
            String beforeSql = "SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH " +
                             "FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_SCHEMA = DATABASE() " +
                             "AND TABLE_NAME = 'alert_log' " +
                             "AND COLUMN_NAME IN ('old_value', 'new_value')";
            
            java.util.List<java.util.Map<String, Object>> beforeFix = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(beforeSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    beforeFix.add(col);
                }
            }
            result.put("beforeFix", beforeFix);
            
            // 2. 执行 ALTER TABLE 修改字段类型
            String alterOldValue = "ALTER TABLE alert_log MODIFY COLUMN old_value TEXT";
            String alterNewValue = "ALTER TABLE alert_log MODIFY COLUMN new_value TEXT";
            
            stmt.executeUpdate(alterOldValue);
            stmt.executeUpdate(alterNewValue);
            
            // 3. 验证修复结果
            java.util.List<java.util.Map<String, Object>> afterFix = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(beforeSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    afterFix.add(col);
                }
            }
            result.put("afterFix", afterFix);
            
            result.put("success", true);
            result.put("message", "字段类型修复成功：alert_log 的 old_value 和 new_value 已修改为 TEXT 类型");
            log.info("[Debug] alert_log 表字段长度修复完成");
            
        } catch (Exception e) {
            log.error("[Debug] 修复 alert_log 表字段长度失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 测试AlertService的processAlerts方法，捕获并返回任何异常
     */
    @PostMapping("/test-alert-service/{asinId}")
    public ResponseEntity<java.util.Map<String, Object>> testAlertService(@PathVariable Long asinId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            // 加载ASIN
            var asinOpt = asinRepository.findById(asinId);
            if (asinOpt.isEmpty()) {
                result.put("success", false);
                result.put("error", "ASIN not found: " + asinId);
                return ResponseEntity.badRequest().body(result);
            }
            var asin = asinOpt.get();
            
            // 获取最新历史记录
            var historyList = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asinId);
            if (historyList.isEmpty()) {
                result.put("success", false);
                result.put("error", "No history found for ASIN: " + asinId);
                return ResponseEntity.badRequest().body(result);
            }
            var latest = historyList.get(0);
            
            // 构造模拟的新快照(稍微修改数据以触发变化检测)
            com.amz.spyglass.scraper.AsinSnapshotDTO mockSnap = new com.amz.spyglass.scraper.AsinSnapshotDTO();
            mockSnap.setTitle(latest.getTitle() + " [MODIFIED]");
            mockSnap.setPrice(latest.getPrice() == null ? null : latest.getPrice().add(new java.math.BigDecimal("1.00")));
            mockSnap.setBsr(latest.getBsr());
            mockSnap.setBsrCategory(latest.getBsrCategory());
            mockSnap.setBsrSubcategory(latest.getBsrSubcategory());
            mockSnap.setBsrSubcategoryRank(latest.getBsrSubcategoryRank());
            mockSnap.setInventory(latest.getInventory());
            mockSnap.setBulletPoints(latest.getBulletPoints() + " [MODIFIED]");
            mockSnap.setImageMd5(latest.getImageMd5() == null ? "modified" : latest.getImageMd5() + "X");
            mockSnap.setAplusMd5(latest.getAplusMd5() == null ? "modified" : latest.getAplusMd5() + "X");
            mockSnap.setTotalReviews(latest.getTotalReviews());
            mockSnap.setAvgRating(latest.getAvgRating());
            mockSnap.setLatestNegativeReviewMd5(latest.getLatestNegativeReviewMd5());
            mockSnap.setSnapshotAt(java.time.Instant.now());
            
            // 调用AlertService
            log.info("[Debug] 测试调用 AlertService.processAlerts for ASIN_ID={}", asinId);
            com.amz.spyglass.alert.AlertService alertService = applicationContext.getBean(com.amz.spyglass.alert.AlertService.class);
            alertService.processAlerts(asin, mockSnap);
            
            result.put("success", true);
            result.put("message", "AlertService executed successfully");
            result.put("asinId", asinId);
            result.put("historyCount", historyList.size());
            
        } catch (Exception e) {
            log.error("[Debug] AlertService测试失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorClass", e.getClass().getName());
            result.put("stackTrace", java.util.Arrays.stream(e.getStackTrace())
                .limit(10)
                .map(StackTraceElement::toString)
                .toList());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 查询 alert_log 表结构与统计信息（用于生产环境验证字段长度与告警写入情况）
     */
    @GetMapping("/alert-log/info")
    public ResponseEntity<java.util.Map<String, Object>> getAlertLogInfo() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // 1. 表结构
            String structureSql = "SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                    "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_log' ORDER BY ORDINAL_POSITION";
            java.util.List<java.util.Map<String, Object>> columns = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(structureSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    columns.add(col);
                }
            }
            result.put("columns", columns);

            // 2. 总记录数
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) total FROM alert_log")) {
                if (rs.next()) {
                    result.put("totalRecords", rs.getInt("total"));
                }
            }

            // 3. 按 alert_type 分组
            java.util.Map<String, Integer> byType = new java.util.LinkedHashMap<>();
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT alert_type, COUNT(*) cnt FROM alert_log GROUP BY alert_type")) {
                while (rs.next()) {
                    byType.put(rs.getString("alert_type"), rs.getInt("cnt"));
                }
            }
            result.put("countByType", byType);

            // 4. 最近5条
            String recentSql = "SELECT id, asin_id, alert_type, LENGTH(old_value) old_len, LENGTH(new_value) new_len, " +
                    "LEFT(old_value,50) old_preview, LEFT(new_value,50) new_preview, created_at FROM alert_log ORDER BY created_at DESC LIMIT 5";
            java.util.List<java.util.Map<String, Object>> recent = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(recentSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> rec = new java.util.LinkedHashMap<>();
                    rec.put("id", rs.getLong("id"));
                    rec.put("asinId", rs.getLong("asin_id"));
                    rec.put("alertType", rs.getString("alert_type"));
                    rec.put("oldLength", rs.getInt("old_len"));
                    rec.put("newLength", rs.getInt("new_len"));
                    rec.put("oldPreview", rs.getString("old_preview"));
                    rec.put("newPreview", rs.getString("new_preview"));
                    rec.put("createdAt", rs.getTimestamp("created_at"));
                    recent.add(rec);
                }
            }
            result.put("recentRecords", recent);

            result.put("success", true);
        } catch (Exception e) {
            log.error("[Debug] 查询 alert_log 表信息失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 查询 asin_history 表结构与最近快照（便于确认调度是否写入快照）
     */
    @GetMapping("/asin-history/info")
    public ResponseEntity<java.util.Map<String, Object>> getAsinHistoryInfo() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // 表结构
            String structureSql = "SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                    "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asin_history' ORDER BY ORDINAL_POSITION";
            java.util.List<java.util.Map<String, Object>> columns = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(structureSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("COLUMN_TYPE"));
                    col.put("maxLength", rs.getObject("CHARACTER_MAXIMUM_LENGTH"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    columns.add(col);
                }
            }
            result.put("columns", columns);

            // 总数
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) total FROM asin_history")) {
                if (rs.next()) {
                    result.put("totalRecords", rs.getInt("total"));
                }
            }

            // 最近5条快照
            String recentSql = "SELECT id, asin_id, price, snapshot_at, LENGTH(title) title_len, LENGTH(bullet_points) bp_len, inventory " +
                    "FROM asin_history ORDER BY snapshot_at DESC LIMIT 5";
            java.util.List<java.util.Map<String, Object>> recent = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(recentSql)) {
                while (rs.next()) {
                    java.util.Map<String, Object> rec = new java.util.LinkedHashMap<>();
                    rec.put("id", rs.getLong("id"));
                    rec.put("asinId", rs.getLong("asin_id"));
                    rec.put("price", rs.getBigDecimal("price"));
                    rec.put("snapshotAt", rs.getTimestamp("snapshot_at"));
                    rec.put("titleLength", rs.getInt("title_len"));
                    rec.put("bulletPointsLength", rs.getInt("bp_len"));
                    rec.put("inventory", rs.getObject("inventory"));
                    recent.add(rec);
                }
            }
            result.put("recentSnapshots", recent);

            result.put("success", true);
        } catch (Exception e) {
            log.error("[Debug] 查询 asin_history 表信息失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
