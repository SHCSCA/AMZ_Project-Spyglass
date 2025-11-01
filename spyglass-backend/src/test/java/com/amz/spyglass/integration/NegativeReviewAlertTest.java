package com.amz.spyglass.integration;

import com.amz.spyglass.alert.AlertService;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.alert.ChangeAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 NEGATIVE_REVIEW MD5 变化触发 ChangeAlert。
 */
@SpringBootTest(classes = com.amz.spyglass.TestSpyglassApplication.class)
@ActiveProfiles("test")
public class NegativeReviewAlertTest {

    @Autowired
    private AsinRepository asinRepository;
    @Autowired
    private AsinHistoryRepository historyRepository;
    @Autowired
    private ChangeAlertRepository changeAlertRepository;
    @Autowired
    private AlertService alertService;

    private AsinModel asin;

    @BeforeEach
    void init() {
        asin = new AsinModel();
        asin.setAsin("B0NEGREV001");
        asin.setSite("US");
        asinRepository.save(asin);

        AsinHistoryModel baseline = new AsinHistoryModel();
        baseline.setAsin(asin);
        baseline.setLatestNegativeReviewMd5("neg_md5_old");
        baseline.setSnapshotAt(Instant.now().minusSeconds(200));
        historyRepository.save(baseline);
    }

    @Test
    void shouldTriggerNegativeReviewChangeAlert() {
        com.amz.spyglass.scraper.AsinSnapshotDTO changed = new com.amz.spyglass.scraper.AsinSnapshotDTO();
        changed.setLatestNegativeReviewMd5("neg_md5_new");

        alertService.processAlerts(asin, changed);

        assertThat(changeAlertRepository.count()).isEqualTo(1);
        var alert = changeAlertRepository.findAll().getFirst();
        assertThat(alert.getAlertType()).isEqualTo("NEGATIVE_REVIEW");
        assertThat(alert.getOldValue()).isEqualTo("neg_md5_old");
        assertThat(alert.getNewValue()).isEqualTo("neg_md5_new");
    }
}
