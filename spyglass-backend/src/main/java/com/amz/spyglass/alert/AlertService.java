package com.amz.spyglass.alert;

import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import com.amz.spyglass.model.alert.ChangeAlert;
import com.amz.spyglass.model.alert.PriceAlert;
import com.amz.spyglass.repository.alert.ChangeAlertRepository;
import com.amz.spyglass.repository.alert.PriceAlertRepository;
import com.amz.spyglass.repository.alert.AlertLogRepository;
import com.amz.spyglass.model.alert.AlertLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 告警服务：对比新旧快照并触发告警（包括钉钉推送和价格记录入库）
 */
@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final AsinHistoryRepository historyRepository;
    private final DingTalkPusher dingTalkPusher;
    private final PriceAlertRepository priceAlertRepository;
    private final ChangeAlertRepository changeAlertRepository;
    private final AlertLogRepository alertLogRepository;


    public AlertService(AsinHistoryRepository historyRepository, DingTalkPusher dingTalkPusher, PriceAlertRepository priceAlertRepository, ChangeAlertRepository changeAlertRepository, AlertLogRepository alertLogRepository) {
        this.historyRepository = historyRepository;
        this.dingTalkPusher = dingTalkPusher;
        this.priceAlertRepository = priceAlertRepository;
        this.changeAlertRepository = changeAlertRepository;
        this.alertLogRepository = alertLogRepository;
    }

    @Transactional
    public void processAlerts(AsinModel asin, AsinSnapshotDTO newSnap) {
        String cid = UUID.randomUUID().toString().substring(0,8); // 简短 correlationId
        // 获取最近一条历史记录
        List<AsinHistoryModel> rows = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asin.getId());
        AsinHistoryModel last = rows.isEmpty() ? null : rows.getFirst();

        // 如果没有历史快照，视为首次抓取：仅可选择发送“初始化监控”提示（当前不发送，直接返回）
        if (last == null) {
            logger.debug("[AlertDebug cid={}] ASIN={} 首次抓取，跳过对比", cid, asin.getAsin());
            return;
        }

        // 价格变动
        checkPriceChange(cid, asin, last, newSnap);

        // 库存阈值告警
        checkInventoryThreshold(cid, asin, newSnap);

        // 其他字段变更告警
        checkFieldChanges(cid, asin, last, newSnap);
    }

    private void checkPriceChange(String cid, AsinModel asin, AsinHistoryModel last, AsinSnapshotDTO newSnap) {
        BigDecimal oldPrice = last.getPrice();
        BigDecimal newPrice = newSnap.getPrice();

        logger.debug("[AlertDebug cid={}] checkPriceChange asin={} oldPrice={} newPrice={}", cid, asin.getAsin(), oldPrice, newPrice);

        if (newPrice != null && oldPrice != null && newPrice.compareTo(oldPrice) != 0) {
            BigDecimal changePercent = BigDecimal.ZERO;
            if (oldPrice.compareTo(BigDecimal.ZERO) != 0) {
                changePercent = newPrice.subtract(oldPrice)
                        .divide(oldPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
            PriceAlert alert = new PriceAlert(asin.getId(), oldPrice, newPrice, changePercent);
            // 填充上下文字段（旧值来自 last，新值来自 newSnap）
            alert.setOldTitle(last.getTitle());
            alert.setNewTitle(newSnap.getTitle());
            alert.setOldImageMd5(last.getImageMd5());
            alert.setNewImageMd5(newSnap.getImageMd5());
            alert.setOldBulletPoints(last.getBulletPoints());
            alert.setNewBulletPoints(newSnap.getBulletPoints());
            alert.setOldAplusMd5(last.getAplusMd5());
            alert.setNewAplusMd5(newSnap.getAplusMd5());

        priceAlertRepository.save(alert);
        // 统一日志
        AlertLog logRow = new AlertLog();
        logRow.setAsinId(asin.getId());
        logRow.setAsinCode(asin.getAsin());
        logRow.setSite(asin.getSite());
        logRow.setAlertType("PRICE_CHANGE");
        logRow.setOldValue(oldPrice == null ? null : oldPrice.toPlainString());
        logRow.setNewValue(newPrice == null ? null : newPrice.toPlainString());
        logRow.setChangePercent(changePercent.setScale(2, RoundingMode.HALF_UP));
        logRow.setMessage("价格变化 " + oldPrice + " -> " + newPrice);
        String ctx = buildContextJson(asin.getAsin(), "PRICE", last.getPrice()==null?null:last.getPrice().toPlainString(), newSnap.getPrice()==null?null:newSnap.getPrice().toPlainString(), last.getPrice()==null?null:last.getPrice().doubleValue(), newSnap.getPrice()==null?null:newSnap.getPrice().doubleValue());
        logRow.setContextJson(ctx);
        alertLogRepository.save(logRow);
            logger.info("[Alert cid={}] PRICE-CHANGE recorded ASIN={} Old={} New={} Δ%={} titleOld='{}' titleNew='{}'", cid, asin.getAsin(), oldPrice, newPrice, changePercent.setScale(2, RoundingMode.HALF_UP), truncate(last.getTitle()), truncate(newSnap.getTitle()));

            dingTalkPusher.pushText("价格变动告警: " + asin.getNicknameOrAsin(),
                    String.format("ASIN: %s\n旧价: %s\n新价: %s\n变化: %s%%\n旧标题: %s\n新标题: %s", asin.getAsin(), oldPrice, newPrice, changePercent.setScale(2, RoundingMode.HALF_UP), last.getTitle(), newSnap.getTitle()));
        }
    }

    private void checkInventoryThreshold(String cid, AsinModel asin, AsinSnapshotDTO newSnap) {
        Integer inv = newSnap.getInventory();
        Integer threshold = asin.getInventoryThreshold();
        if (inv != null && threshold != null && inv < threshold) {
            dingTalkPusher.pushText("库存告警: " + asin.getNicknameOrAsin(),
                    String.format("ASIN: %s\n当前库存: %d\n低于阈值: %d", asin.getAsin(), inv, threshold));
            logger.info("[Alert cid={}] INVENTORY-LOW ASIN={} inv={} threshold={}", cid, asin.getAsin(), inv, threshold);
        }
    }

    private void checkFieldChanges(String cid, AsinModel asin, AsinHistoryModel last, AsinSnapshotDTO newSnap) {
        logger.debug("[AlertDebug cid={}] checkFieldChanges asin={} lastTitle='{}' newTitle='{}' lastImage={} newImage={} lastAplus={} newAplus={} lastBP.len={} newBP.len={} lastNegRev={} newNegRev={}",
            cid, asin.getAsin(), truncate(last.getTitle()), truncate(newSnap.getTitle()), last.getImageMd5(), newSnap.getImageMd5(), last.getAplusMd5(), newSnap.getAplusMd5(),
            lengthOrNull(last.getBulletPoints()), lengthOrNull(newSnap.getBulletPoints()), last.getLatestNegativeReviewMd5(), newSnap.getLatestNegativeReviewMd5());
        compareAndAlert(cid, asin, "TITLE", last.getTitle(), newSnap.getTitle(), "标题变更告警", "旧标题: %s\n新标题: %s");
        compareAndAlert(cid, asin, "MAIN_IMAGE", last.getImageMd5(), newSnap.getImageMd5(), "主图变更", "旧主图MD5: %s\n新主图MD5: %s");
        compareAndAlert(cid, asin, "BULLET_POINTS", last.getBulletPoints(), newSnap.getBulletPoints(), "五点要点变更", "旧五点:\n%s\n新五点:\n%s");
        compareAndAlert(cid, asin, "APLUS_CONTENT", last.getAplusMd5(), newSnap.getAplusMd5(), "A+ 内容变更", "旧 A+ MD5: %s\n新 A+ MD5: %s");
        compareAndAlert(cid, asin, "NEGATIVE_REVIEW", last.getLatestNegativeReviewMd5(), newSnap.getLatestNegativeReviewMd5(), "新差评告警", "检测到新的差评，需要及时关注。 (MD5: %s -> %s)");
    }

    private void compareAndAlert(String cid, AsinModel asin, String alertType, String oldValue, String newValue, String dingTalkTitle, String dingTalkFormat) {
        logger.debug("[AlertDebug cid={}] compareAndAlert asin={} type={} old='{}' new='{}' equal={}", cid, asin.getAsin(), alertType, truncate(oldValue), truncate(newValue), Objects.equals(oldValue, newValue));
        if (newValue != null && !Objects.equals(oldValue, newValue)) {
            ChangeAlert alert = new ChangeAlert(asin.getId(), alertType, oldValue, newValue);
            changeAlertRepository.save(alert);
            AlertLog logRow = new AlertLog();
            logRow.setAsinId(asin.getId());
            logRow.setAsinCode(asin.getAsin());
            logRow.setSite(asin.getSite());
            logRow.setAlertType(alertType);
            logRow.setOldValue(oldValue);
            logRow.setNewValue(newValue);
            logRow.setMessage(alertType + " 变更");
            // 统一上下文字段：field 变更结构化 JSON
            String ctx = buildContextJson(asin.getAsin(), alertType, oldValue, newValue, null, null);
            logRow.setContextJson(ctx);
            alertLogRepository.save(logRow);
            logger.info("[Alert cid={}] {} CHANGE recorded ASIN={} Old='{}' New='{}'", cid, alertType, asin.getAsin(), truncate(oldValue), truncate(newValue));

            String message = String.format("ASIN: %s\n", asin.getAsin()) + String.format(dingTalkFormat, oldValue, newValue);
            dingTalkPusher.pushText(dingTalkTitle + ": " + asin.getNicknameOrAsin(), message);
        }
    }

    // ---- 调试辅助方法 ----
    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 60 ? s : s.substring(0,57) + "...";
    }
    private Integer lengthOrNull(String s) { return s == null ? null : s.length(); }
    private String safe(String s) { return s == null ? "" : s; }
    private String quote(String s) { return "\"" + s.replace("\"","\\\"") + "\""; }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private String buildContextJson(String asin, String field, String oldVal, String newVal, Double oldPrice, Double newPrice) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("asin", asin);
        if (field != null) map.put("field", field);
        if (oldVal != null) map.put("oldValue", oldVal);
        if (newVal != null) map.put("newValue", newVal);
        if (oldPrice != null) map.put("oldPrice", oldPrice);
        if (newPrice != null) map.put("newPrice", newPrice);
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            logger.warn("buildContextJson serialization failed, fallback to map.toString() asin={} field={}", asin, field);
            return map.toString();
        }
    }
}
