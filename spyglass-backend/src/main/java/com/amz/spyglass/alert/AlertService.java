package com.amz.spyglass.alert;

import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

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
        AsinHistoryModel last = rows.isEmpty() ? null : rows.getFirst();

        // 价格变动
        BigDecimal oldPrice = last == null ? null : last.getPrice();
        BigDecimal newPrice = newSnap.getPrice();
        if (newPrice != null && oldPrice != null && newPrice.compareTo(oldPrice) != 0) {
            // 保存到 price_alert 表
            try {
                jdbcTemplate.update("INSERT INTO price_alert (asin_id, old_price, new_price, change_percent, alert_at, created_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                        asin.getId(), oldPrice, newPrice,
                        oldPrice.doubleValue() == 0.0 ? 0.0 : (newPrice.subtract(oldPrice).divide(oldPrice, 4, RoundingMode.HALF_UP).doubleValue() * 100)
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

        // 以下为字段级别变更告警：标题、五点要点、A+、主图
        if (last != null) {
            // title
            String oldTitle = last.getTitle();
            String newTitle = newSnap.getTitle();
            if (!Objects.equals(oldTitle, newTitle)) {
                dingTalkPusher.pushText("标题变更告警: " + asin.getAsin(), "旧标题: " + (oldTitle == null ? "" : oldTitle) + "\n新标题: " + (newTitle == null ? "" : newTitle));
            }

            // bullet points
            String oldBullets = last.getBulletPoints();
            String newBullets = newSnap.getBulletPoints();
            if (!Objects.equals(oldBullets, newBullets)) {
                dingTalkPusher.pushText("五点要点变更: " + asin.getAsin(), "旧五点:\n" + (oldBullets == null ? "" : oldBullets) + "\n新五点:\n" + (newBullets == null ? "" : newBullets));
            }

            // A+ 区域 MD5
            String oldAplus = last.getAplusMd5();
            String newAplus = newSnap.getAplusMd5();
            if (!Objects.equals(oldAplus, newAplus)) {
                dingTalkPusher.pushText("A+ 内容变更: " + asin.getAsin(), "旧 A+ MD5: " + (oldAplus == null ? "" : oldAplus) + "\n新 A+ MD5: " + (newAplus == null ? "" : newAplus));
            }

            // 新差评检测（比较新旧差评的 MD5）
            String oldReviewMd5 = last.getLatestNegativeReviewMd5();
            String newReviewMd5 = newSnap.getLatestNegativeReviewMd5();
            if (!Objects.equals(oldReviewMd5, newReviewMd5) && newReviewMd5 != null) {
                dingTalkPusher.pushText("新差评告警: " + asin.getAsin(), "检测到新的差评，需要及时关注。");
            }

            // 主图 MD5
            String oldImg = last.getImageMd5();
            String newImg = newSnap.getImageMd5();
            if (!Objects.equals(oldImg, newImg)) {
                dingTalkPusher.pushText("主图变更: " + asin.getAsin(), "旧主图MD5: " + (oldImg == null ? "" : oldImg) + "\n新主图MD5: " + (newImg == null ? "" : newImg));
            }
        }
    }

    public void compareAndAlert(AsinModel asin, AsinSnapshotDTO newSnap) {
        processAlerts(asin, newSnap);
    }
}
