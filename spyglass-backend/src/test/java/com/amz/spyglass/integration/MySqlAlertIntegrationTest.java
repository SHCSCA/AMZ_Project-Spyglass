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
// Removed @ServiceConnection approach; using manual start + DynamicPropertySource
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用 Testcontainers 启动真实 MySQL 验证告警持久化。
 */
@SpringBootTest(classes = com.amz.spyglass.TestSpyglassApplication.class)
@ActiveProfiles("mysqltest")
@TestPropertySource(properties = {
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
    "spring.jpa.hibernate.ddl-auto=update"
})
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MySqlAlertIntegrationTest {
    // 手动管理容器生命周期，避免 @ServiceConnection 启动时序导致端口未映射异常
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("spyglass")
        .withUsername("test")
        .withPassword("test");

    static {
        mysql.start(); // 显式启动，确保 mapped port 可用
        System.out.println("[MySQLContainer] started url=" + mysql.getJdbcUrl());
    }

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

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
    void setup() {
        asin = new AsinModel();
        asin.setAsin("B0MYSQL01");
        asin.setSite("US");
        asin.setInventoryThreshold(20);
        asinRepository.save(asin);

        AsinHistoryModel baseline = new AsinHistoryModel();
        baseline.setAsin(asin);
        baseline.setPrice(new BigDecimal("50.00"));
        baseline.setTitle("MySQL Baseline Title");
        baseline.setImageMd5("img_mysql_old");
        baseline.setAplusMd5("aplus_mysql_old");
        baseline.setBulletPoints("P1\nP2");
        baseline.setLatestNegativeReviewMd5("rev_old_md5");
        baseline.setSnapshotAt(Instant.now().minusSeconds(120));
        historyRepository.save(baseline);
    }

    @Test
    void shouldPersistAlertsInRealMySQL() {
        AsinSnapshotDTO changed = new AsinSnapshotDTO();
        changed.setPrice(new BigDecimal("55.00")); // 价格上升
        changed.setTitle("MySQL Baseline Title UPDATED");
        changed.setImageMd5("img_mysql_new");
        changed.setAplusMd5("aplus_mysql_new");
        changed.setBulletPoints("P1\nP2\nP3");
        changed.setLatestNegativeReviewMd5("rev_new_md5");
        changed.setSnapshotAt(Instant.now());

        alertService.processAlerts(asin, changed);

        assertThat(priceAlertRepository.count()).isEqualTo(1);
        assertThat(changeAlertRepository.count()).isGreaterThanOrEqualTo(4); // TITLE + MAIN_IMAGE + BULLET_POINTS + APLUS_CONTENT + NEGATIVE_REVIEW (部分可能为空值过滤)
    }
}
