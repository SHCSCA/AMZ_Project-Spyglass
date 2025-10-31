/*
 Navicat MySQL Dump SQL

 Source Server         : amz
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : 156.238.230.229:3306
 Source Schema         : amzspaglass

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 31/10/2025 16:27:21
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for asin
-- ----------------------------
DROP TABLE IF EXISTS `asin`;
CREATE TABLE `asin`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Amazon 标准识别号',
  `site` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点代码(US/UK等)',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '自定义昵称/备注',
  `inventory_threshold` int NULL DEFAULT NULL COMMENT '库存预警阈值',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_asin_site`(`asin` ASC, `site` ASC) USING BTREE COMMENT 'ASIN和站点组合唯一索引'
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '监控目标ASIN管理表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asin_history
-- ----------------------------
DROP TABLE IF EXISTS `asin_history`;
CREATE TABLE `asin_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联的asin表ID',
  `title` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '商品标题',
  `price` decimal(14, 2) NULL DEFAULT NULL COMMENT '当前价格',
  `bsr` int NULL DEFAULT NULL COMMENT 'Best Seller Rank排名',
  `bsr_category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BSR大类名称',
  `bsr_subcategory` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BSR小类名称',
  `bsr_subcategory_rank` int NULL DEFAULT NULL COMMENT 'BSR小类排名',
  `inventory` int NULL DEFAULT NULL COMMENT '预估库存数量',
  `bullet_points` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '商品五点要点（feature bullets），多行文本，按抓取顺序换行存储',
  `image_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主图MD5哈希值',
  `aplus_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'A+页面内容MD5哈希值',
  `total_reviews` int NULL DEFAULT NULL COMMENT '总评论数',
  `avg_rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '平均评分',
  `snapshot_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `updated_at` datetime(6) NOT NULL,
  `latest_negative_review_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_asin_history_asin_id`(`asin_id` ASC) USING BTREE COMMENT 'ASIN ID索引',
  INDEX `idx_asin_history_snapshot_at`(`snapshot_at` ASC) USING BTREE COMMENT '快照时间索引',
  CONSTRAINT `fk_asin_history_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'ASIN监控数据历史快照表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for price_alert
-- ----------------------------
DROP TABLE IF EXISTS `price_alert`;
CREATE TABLE `price_alert`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联的asin表ID',
  `old_price` decimal(14, 2) NULL DEFAULT NULL COMMENT '变动前价格',
  `new_price` decimal(14, 2) NULL DEFAULT NULL COMMENT '变动后价格',
  `change_percent` decimal(5, 2) NULL DEFAULT NULL COMMENT '价格变动百分比',
  `alert_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_price_alert_asin_date`(`asin_id` ASC, `alert_at` ASC) USING BTREE COMMENT 'ASIN和告警时间联合索引',
  CONSTRAINT `fk_price_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '价格变动记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for review_alert
-- ----------------------------
DROP TABLE IF EXISTS `review_alert`;
CREATE TABLE `review_alert`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联的asin表ID',
  `review_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论唯一标识',
  `rating` int NOT NULL COMMENT '评分(1-5星)',
  `review_date` date NOT NULL COMMENT '评论日期',
  `review_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '评论内容',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_review_id`(`review_id` ASC) USING BTREE COMMENT '评论ID唯一索引',
  INDEX `idx_review_alert_asin_rating`(`asin_id` ASC, `rating` ASC) USING BTREE COMMENT 'ASIN和评分联合索引',
  CONSTRAINT `fk_review_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '差评监控记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for scrape_task
-- ----------------------------
DROP TABLE IF EXISTS `scrape_task`;
CREATE TABLE `scrape_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NULL DEFAULT NULL COMMENT '关联的asin表ID',
  `status` enum('PENDING','RUNNING','SUCCESS','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '执行结果消息/错误信息',
  `retry_count` int NULL DEFAULT 0 COMMENT '重试次数',
  `run_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `finished_at` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scrape_task_asin_id`(`asin_id` ASC) USING BTREE COMMENT 'ASIN ID索引',
  INDEX `idx_scrape_task_status`(`status` ASC) USING BTREE COMMENT '任务状态索引',
  CONSTRAINT `fk_scrape_task_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '抓取任务执行记录表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
