package com.amz.spyglass.integration;

import com.amz.spyglass.alert.AlertService;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.alert.ChangeAlertRepository;
import com.amz.spyglass.repository.alert.PriceAlertRepository;
import com.amz.spyglass.scraper.AsinSnapshotDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证当价格与标题发生变化时，PriceAlert 与 ChangeAlert 均被持久化。
 */
@SpringBootTest(classes = com.amz.spyglass.TestSpyglassApplication.class)
@ActiveProfiles("test") // 禁用调度器与外部抓取，避免集成测试访问真实网络
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AlertPersistenceIntegrationTest {

    @Autowired
    private AsinRepository asinRepository;
    @Autowired
    private AsinHistoryRepository historyRepository;
    @Autowired
    private PriceAlertRepository priceAlertRepository;
    @Autowired
    private ChangeAlertRepository changeAlertRepository;
    @Autowired
    private AlertService alertService;

    private AsinModel asin;

    @BeforeEach
    void initData() {
        asin = new AsinModel();
        asin.setAsin("B0INTTEST");
        asin.setNickname("Integration Test ASIN");
        asin.setInventoryThreshold(10);
        // 设置站点，实体中 site 为非空列，避免 DataIntegrityViolation
        asin.setSite("US");
        asin = asinRepository.save(asin);

        AsinHistoryModel baseline = new AsinHistoryModel();
        baseline.setAsin(asin);
        baseline.setPrice(new BigDecimal("100.00"));
        baseline.setTitle("Baseline Title");
        baseline.setImageMd5("img_md5_old");
        baseline.setAplusMd5("aplus_md5_old");
        baseline.setBulletPoints("BP1\nBP2");
        baseline.setLatestNegativeReviewMd5("neg_rev_md5_old");
        baseline.setSnapshotAt(Instant.now().minusSeconds(300));
        historyRepository.save(baseline);
    }

    @Test
    void shouldPersistPriceAlertAndChangeAlertWhenPriceAndTitleChanged() {
        AsinSnapshotDTO changed = new AsinSnapshotDTO();
        changed.setPrice(new BigDecimal("95.00")); // price drop
        changed.setTitle("Baseline Title UPDATED"); // title change
        changed.setInventory(5); // below threshold triggers inventory alert (not persisted table)

        alertService.processAlerts(asin, changed);

        assertThat(priceAlertRepository.count()).isEqualTo(1);
        assertThat(changeAlertRepository.count()).isEqualTo(1);

        var priceAlert = priceAlertRepository.findAll().getFirst();
        assertThat(priceAlert.getOldPrice()).isEqualByComparingTo("100.00");
        assertThat(priceAlert.getNewPrice()).isEqualByComparingTo("95.00");
        assertThat(priceAlert.getOldTitle()).isEqualTo("Baseline Title");
        assertThat(priceAlert.getNewTitle()).isEqualTo("Baseline Title UPDATED");

        var changeAlert = changeAlertRepository.findAll().getFirst();
        assertThat(changeAlert.getAlertType()).isEqualTo("TITLE");
        assertThat(changeAlert.getOldValue()).isEqualTo("Baseline Title");
        assertThat(changeAlert.getNewValue()).isEqualTo("Baseline Title UPDATED");
    }
}
