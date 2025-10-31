package com.amz.spyglass.alert;

import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 单元测试：验证 AlertService 在字段变化时正确调用 DingTalkPusher。
 * 覆盖价格变化、库存低于阈值、标题变化、五点要点变化、主图/A+变化、差评变化等多种场景。
 */
public class AlertServiceTest {

    @Test
    void shouldTriggerAlertsOnChanges() {
        AsinHistoryRepository historyRepo = Mockito.mock(AsinHistoryRepository.class);
        DingTalkPusher pusher = Mockito.mock(DingTalkPusher.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

        // 模拟最近历史快照
        AsinHistoryModel last = new AsinHistoryModel();
        AsinModel asin = new AsinModel();
        asin.setId(1L);
        asin.setAsin("TESTASIN");
        asin.setInventoryThreshold(10);
        last.setAsin(asin);
        last.setPrice(new BigDecimal("20.00"));
        last.setTitle("Old Title");
        last.setImageMd5("md5_old_img");
        last.setAplusMd5("md5_old_aplus");
        last.setBulletPoints("BP1\nBP2");
        last.setLatestNegativeReviewMd5("rev_old");
        last.setSnapshotAt(Instant.now().minusSeconds(3600));
        Mockito.when(historyRepo.findByAsinIdOrderBySnapshotAtDesc(asin.getId())).thenReturn(List.of(last));

        // 新快照（多个字段变化）
        AsinSnapshotDTO snap = new AsinSnapshotDTO();
        snap.setPrice(new BigDecimal("19.50")); // 价格变化
        snap.setTitle("New Title"); // 标题变化
        snap.setInventory(5); // 低于阈值
        snap.setImageMd5("md5_new_img");
        snap.setAplusMd5("md5_new_aplus");
        snap.setBulletPoints("BP1\nBP2\nBP3");
        snap.setLatestNegativeReviewMd5("rev_new");

        AlertService service = new AlertService(historyRepo, pusher, jdbc);
        service.compareAndAlert(asin, snap);

        // 验证 pusher 至少被调用 4 次（价格 + 库存 + 标题 + 其它变化）
        Mockito.verify(pusher, Mockito.atLeast(4)).pushText(Mockito.anyString(), Mockito.anyString());
    }
}
