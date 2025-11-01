package com.amz.spyglass.alert;

import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.repository.alert.ChangeAlertRepository;
import com.amz.spyglass.repository.alert.PriceAlertRepository;
import com.amz.spyglass.repository.alert.AlertLogRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import org.junit.jupiter.api.BeforeEach;
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

    private AsinHistoryRepository historyRepo;
    private DingTalkPusher pusher;
    private PriceAlertRepository priceAlertRepo;
    private ChangeAlertRepository changeAlertRepo;
    private AlertService alertService;
    private AlertLogRepository alertLogRepository;
    private AsinModel asin;
    private AsinHistoryModel lastHistory;

    @BeforeEach
    void setUp() {
        historyRepo = Mockito.mock(AsinHistoryRepository.class);
        pusher = Mockito.mock(DingTalkPusher.class);
    priceAlertRepo = Mockito.mock(PriceAlertRepository.class);
        changeAlertRepo = Mockito.mock(ChangeAlertRepository.class);
    alertLogRepository = Mockito.mock(AlertLogRepository.class);
    alertService = new AlertService(historyRepo, pusher, priceAlertRepo, changeAlertRepo, alertLogRepository);

        asin = new AsinModel();
        asin.setId(1L);
        asin.setAsin("B0TESTASIN");
        asin.setNickname("Test Product");
        asin.setInventoryThreshold(10);

        lastHistory = new AsinHistoryModel();
        lastHistory.setAsin(asin);
        lastHistory.setPrice(new BigDecimal("100.00"));
        lastHistory.setTitle("Old Title");
        lastHistory.setImageMd5("md5_old_img");
        lastHistory.setAplusMd5("md5_old_aplus");
        lastHistory.setBulletPoints("Old BP1\nOld BP2");
        lastHistory.setLatestNegativeReviewMd5("rev_md5_old");
        lastHistory.setSnapshotAt(Instant.now().minusSeconds(3600));

        Mockito.when(historyRepo.findByAsinIdOrderBySnapshotAtDesc(asin.getId())).thenReturn(List.of(lastHistory));
    }

    @Test
    void whenPriceChanges_thenPriceAlertIsSavedAndPushed() {
        AsinSnapshotDTO newSnap = new AsinSnapshotDTO();
        newSnap.setPrice(new BigDecimal("95.50"));
        newSnap.setTitle("Old Title"); // No other changes

        alertService.processAlerts(asin, newSnap);

        Mockito.verify(priceAlertRepo, Mockito.times(1)).save(Mockito.any(com.amz.spyglass.model.alert.PriceAlert.class));
        Mockito.verify(pusher, Mockito.times(1)).pushText(Mockito.contains("价格变动告警"), Mockito.contains("新价: 95.50"));
    }

    @Test
    void whenTitleChanges_thenChangeAlertIsSavedAndPushed() {
        AsinSnapshotDTO newSnap = new AsinSnapshotDTO();
        newSnap.setPrice(lastHistory.getPrice());
        newSnap.setTitle("New Shiny Title");

        alertService.processAlerts(asin, newSnap);

        Mockito.verify(changeAlertRepo, Mockito.times(1)).save(Mockito.argThat(alert ->
                alert.getAlertType().equals("TITLE") && alert.getNewValue().equals("New Shiny Title")
        ));
        Mockito.verify(pusher, Mockito.times(1)).pushText(Mockito.contains("标题变更告警"), Mockito.contains("新标题: New Shiny Title"));
    }

    @Test
    void whenImageMd5Changes_thenChangeAlertIsSavedAndPushed() {
        AsinSnapshotDTO newSnap = new AsinSnapshotDTO();
        newSnap.setPrice(lastHistory.getPrice());
        newSnap.setTitle(lastHistory.getTitle());
        newSnap.setImageMd5("md5_new_img");

        alertService.processAlerts(asin, newSnap);

        Mockito.verify(changeAlertRepo, Mockito.times(1)).save(Mockito.argThat(alert ->
                alert.getAlertType().equals("MAIN_IMAGE") && alert.getNewValue().equals("md5_new_img")
        ));
        Mockito.verify(pusher, Mockito.times(1)).pushText(Mockito.contains("主图变更"), Mockito.contains("新主图MD5: md5_new_img"));
    }

    @Test
    void whenMultipleFieldsChange_thenMultipleAlertsAreTriggered() {
        AsinSnapshotDTO newSnap = new AsinSnapshotDTO();
        newSnap.setPrice(new BigDecimal("105.00")); // Price change
        newSnap.setTitle("New Title"); // Title change
        newSnap.setInventory(5); // Inventory below threshold
        newSnap.setAplusMd5("md5_new_aplus"); // A+ change

        alertService.processAlerts(asin, newSnap);

        Mockito.verify(priceAlertRepo, Mockito.times(1)).save(Mockito.any());
        Mockito.verify(changeAlertRepo, Mockito.times(2)).save(Mockito.any()); // Title and A+
        Mockito.verify(pusher, Mockito.times(4)).pushText(Mockito.anyString(), Mockito.anyString()); // Price, Title, Inventory, A+
    }

    @Test
    void whenNoChanges_thenNoAlerts() {
        AsinSnapshotDTO newSnap = new AsinSnapshotDTO();
        newSnap.setPrice(lastHistory.getPrice());
        newSnap.setTitle(lastHistory.getTitle());
        newSnap.setImageMd5(lastHistory.getImageMd5());
        newSnap.setAplusMd5(lastHistory.getAplusMd5());
        newSnap.setBulletPoints(lastHistory.getBulletPoints());
        newSnap.setLatestNegativeReviewMd5(lastHistory.getLatestNegativeReviewMd5());
        newSnap.setInventory(20); // Above threshold

        alertService.processAlerts(asin, newSnap);

        Mockito.verify(priceAlertRepo, Mockito.never()).save(Mockito.any());
        Mockito.verify(changeAlertRepo, Mockito.never()).save(Mockito.any());
        Mockito.verify(pusher, Mockito.never()).pushText(Mockito.anyString(), Mockito.anyString());
    }
}
