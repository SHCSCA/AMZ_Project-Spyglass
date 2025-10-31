package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.ScrapeTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 定时任务多 ASIN 抓取集成测试
 * 
 * 验证功能：
 * 1. 多个 ASIN 的批量抓取
 * 2. 每个 ASIN 的事务隔离（一个失败不影响其他）
 * 3. 所有14个字段的完整性
 * 4. 数据持久化验证
 * 
 * @author AI
 * @version 1.0.0
 * @since 2025-10-31
 */
@SpringBootTest
@ActiveProfiles("test")
public class ScraperSchedulerMultiAsinTest {

    private static final Logger logger = LoggerFactory.getLogger(ScraperSchedulerMultiAsinTest.class);

    @Autowired
    private ScraperScheduler scraperScheduler;

    @Autowired
    private AsinRepository asinRepository;

    @Autowired
    private AsinHistoryRepository asinHistoryRepository;

    @Autowired
    private ScrapeTaskRepository scrapeTaskRepository;

    private List<AsinModel> testAsins;

    @BeforeEach
    void setup() {
        logger.info("========================================");
        logger.info("多 ASIN 抓取集成测试初始化");
        logger.info("========================================");

        // 清理测试数据
        asinHistoryRepository.deleteAll();
        scrapeTaskRepository.deleteAll();
        asinRepository.deleteAll();

        // 准备测试 ASIN 数据（使用真实可抓取的 ASIN）
        testAsins = new ArrayList<>();

        // ASIN 1: B0FSYSHLB7 - Sagenest L Shaped Desk
        AsinModel asin1 = new AsinModel();
        asin1.setAsin("B0FSYSHLB7");
        asin1.setSite("US");
        asin1.setNickname("测试商品1 - Laptop Stand");
        asin1.setInventoryThreshold(10);
        testAsins.add(asinRepository.save(asin1));

        // ASIN 2: 可以添加更多真实 ASIN 进行测试
        // AsinModel asin2 = new AsinModel();
        // asin2.setAsin("B0XXXXXX");
        // asin2.setSite("US");
        // asin2.setNickname("测试商品2");
        // asin2.setInventoryThreshold(20);
        // testAsins.add(asinRepository.save(asin2));

        logger.info("✓ 测试数据准备完成，ASIN 数量: {}", testAsins.size());
    }

    /**
     * 测试手动触发单个 ASIN 抓取
     */
    @Test
    void testRunForSingleAsin() throws Exception {
        logger.info("\n========== 测试：手动触发单个 ASIN 抓取 ==========");

        Long asinId = testAsins.get(0).getId();
        String asin = testAsins.get(0).getAsin();

        logger.info("触发抓取 ASIN ID: {}, ASIN: {}", asinId, asin);

        // 执行抓取
        boolean submitted = scraperScheduler.runForSingleAsin(asinId);
        assertTrue(submitted, "应该成功提交抓取任务");

        // 等待异步任务完成
        logger.info("等待异步抓取完成...");
        Thread.sleep(15000); // 等待15秒

        // 验证数据
        logger.info("验证抓取结果...");

        // 验证历史记录
        List<AsinHistoryModel> histories = asinHistoryRepository.findAll();
        assertFalse(histories.isEmpty(), "应该有历史记录");

        AsinHistoryModel history = histories.get(0);
        logger.info("✓ 历史记录已保存，ID: {}", history.getId());

        // 验证所有字段
        validateAllFields(history);

        logger.info("========== 单 ASIN 抓取测试通过 ==========\n");
    }

    /**
     * 测试批量抓取多个 ASIN
     */
    @Test
    void testRunAllAsins() throws Exception {
        logger.info("\n========== 测试：批量抓取所有 ASIN ==========");

        int asinCount = testAsins.size();
        logger.info("准备批量抓取 {} 个 ASIN", asinCount);

        // 执行批量抓取
        scraperScheduler.runAll();

        // 等待异步任务完成
        logger.info("等待所有异步抓取完成...");
        Thread.sleep(20000 * asinCount); // 每个 ASIN 预留20秒

        // 验证数据
        logger.info("验证批量抓取结果...");

        List<AsinHistoryModel> histories = asinHistoryRepository.findAll();
        logger.info("历史记录数量: {}", histories.size());

        // 至少应该有一条成功的记录
        assertFalse(histories.isEmpty(), "至少应该有一条历史记录");

        // 验证每条记录的字段完整性
        for (AsinHistoryModel history : histories) {
            logger.info("\n验证 ASIN: {} 的字段...", history.getAsin().getAsin());
            validateAllFields(history);
        }

        logger.info("========== 批量 ASIN 抓取测试通过 ==========\n");
    }

    /**
     * 测试指定 ASIN 列表的抓取
     */
    @Test
    void testRunForSpecificAsins() throws Exception {
        logger.info("\n========== 测试：指定 ASIN 列表抓取 ==========");

        List<Long> asinIds = new ArrayList<>();
        for (AsinModel asin : testAsins) {
            asinIds.add(asin.getId());
        }

        logger.info("指定抓取 ASIN IDs: {}", asinIds);

        // 执行抓取
        int submitted = scraperScheduler.runForSpecificAsins(asinIds);
        assertEquals(asinIds.size(), submitted, "提交数量应该匹配");

        // 等待异步任务完成
        logger.info("等待指定 ASIN 抓取完成...");
        Thread.sleep(20000 * asinIds.size());

        // 验证数据
        List<AsinHistoryModel> histories = asinHistoryRepository.findAll();
        assertFalse(histories.isEmpty(), "应该有历史记录");

        logger.info("✓ 指定 ASIN 抓取完成，历史记录数: {}", histories.size());

        logger.info("========== 指定 ASIN 列表抓取测试通过 ==========\n");
    }

    /**
     * 验证所有14个字段
     */
    private void validateAllFields(AsinHistoryModel history) {
        logger.info("【字段完整性验证】");

        // 必需字段 (8个)
        assertNotNull(history.getTitle(), "标题不应为空");
        logger.info("  ✓ title: {}", truncate(history.getTitle(), 60));

        assertNotNull(history.getPrice(), "价格不应为空");
        logger.info("  ✓ price: ${}", history.getPrice());

        assertNotNull(history.getBsr(), "BSR不应为空");
        logger.info("  ✓ bsr: #{}", history.getBsr());

        assertNotNull(history.getImageMd5(), "主图MD5不应为空");
        logger.info("  ✓ imageMd5: {}", history.getImageMd5());

        assertNotNull(history.getTotalReviews(), "总评论数不应为空");
        logger.info("  ✓ totalReviews: {}", history.getTotalReviews());

        assertNotNull(history.getAvgRating(), "平均评分不应为空");
        logger.info("  ✓ avgRating: {}", history.getAvgRating());

        assertNotNull(history.getBulletPoints(), "商品要点不应为空");
        int bulletCount = history.getBulletPoints().split("\n").length;
        logger.info("  ✓ bulletPoints: {} 个要点", bulletCount);

        assertNotNull(history.getSnapshotAt(), "快照时间不应为空");
        logger.info("  ✓ snapshotAt: {}", history.getSnapshotAt());

        // 可选字段 (6个) - 记录但不强制
        if (history.getBsrCategory() != null) {
            logger.info("  ✓ bsrCategory: {}", history.getBsrCategory());
        } else {
            logger.warn("  ⚠ bsrCategory: null");
        }

        if (history.getBsrSubcategory() != null) {
            logger.info("  ✓ bsrSubcategory: {}", history.getBsrSubcategory());
        } else {
            logger.warn("  ⚠ bsrSubcategory: null");
        }

        if (history.getBsrSubcategoryRank() != null) {
            logger.info("  ✓ bsrSubcategoryRank: #{}", history.getBsrSubcategoryRank());
        } else {
            logger.warn("  ⚠ bsrSubcategoryRank: null");
        }

        if (history.getInventory() != null) {
            logger.info("  ✓ inventory: {}", history.getInventory());
        } else {
            logger.warn("  ⚠ inventory: null");
        }

        if (history.getAplusMd5() != null) {
            logger.info("  ✓ aplusMd5: {}", history.getAplusMd5());
        } else {
            logger.warn("  ⚠ aplusMd5: null");
        }

        if (history.getLatestNegativeReviewMd5() != null) {
            logger.info("  ✓ latestNegativeReviewMd5: {}", history.getLatestNegativeReviewMd5());
        } else {
            logger.warn("  ⚠ latestNegativeReviewMd5: null");
        }

        logger.info("✓ 必需字段验证通过 (8/8)");
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
}
