package com.amz.spyglass.alert;

import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 告警服务：对比新旧快照并触发告警（包括钉钉推送和价格记录入库）
 */
@Service
public class AlertService {

    private final AsinHistoryRepository historyRepository;
    private final DingTalkPusher dingTalkPusher;
    private final JdbcTemplate jdbcTemplate;

    public AlertService(AsinHistoryRepository historyRepository, DingTalkPusher dingTalkPusher, JdbcTemplate jdbcTemplate) {
        this.historyRepository = historyRepository;
        this.dingTalkPusher = dingTalkPusher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void processAlerts(AsinModel asin, AsinSnapshotDTO newSnap) {
        // 获取最近一条历史记录
        List<AsinHistoryModel> rows = historyRepository.findByAsinIdOrderBySnapshotAtDesc(asin.getId());
        AsinHistoryModel last = rows.isEmpty() ? null : rows.get(0);

        // 价格变动
        BigDecimal oldPrice = last == null ? null : last.getPrice();
        BigDecimal newPrice = newSnap.getPrice();
        if (newPrice != null && oldPrice != null && newPrice.compareTo(oldPrice) != 0) {
            // 保存到 price_alert 表
            try {
                jdbcTemplate.update("INSERT INTO price_alert (asin_id, old_price, new_price, change_percent, alert_at, created_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                        asin.getId(), oldPrice, newPrice,
                        oldPrice.doubleValue() == 0.0 ? 0.0 : (newPrice.subtract(oldPrice).divide(oldPrice, 4, BigDecimal.ROUND_HALF_UP).doubleValue() * 100)
                );
            } catch (Exception ignored) {}

            // 发送钉钉告警
            dingTalkPusher.pushText("价格变动告警: " + asin.getAsin(), "旧价: " + oldPrice + " 新价: " + newPrice);
        }

        // 库存阈值告警（如果配置了阈值）
        Integer inv = newSnap.getInventory();
        Integer threshold = asin.getInventoryThreshold();
        if (inv != null && threshold != null && inv < threshold) {
            dingTalkPusher.pushText("库存告警: " + asin.getAsin(), "当前库存: " + inv + " 小于阈值: " + threshold);
        }
    }
}
