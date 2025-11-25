/*
 Navicat Premium Dump SQL

 Source Server         : amz
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : shcamz.xyz:3306
 Source Schema         : amzspaglass

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 24/11/2025 17:14:01
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for alert_log
-- ----------------------------
DROP TABLE IF EXISTS `alert_log`;
CREATE TABLE `alert_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `asin_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ASIN 字符串本体',
  `site` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点',
  `alert_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '告警类型',
  `severity` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '严重级别: INFO/WARN/CRITICAL',
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `old_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `new_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `change_percent` decimal(8, 2) NULL DEFAULT NULL COMMENT '价格变化百分比(仅价格告警使用)',
  `ref_id` bigint NULL DEFAULT NULL COMMENT '关联原始告警表记录 ID (price_alert/change_alert)',
  `context_json` json NULL COMMENT '结构化上下文 JSON',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_alert_log_asin_type`(`asin_id` ASC, `alert_type` ASC) USING BTREE,
  CONSTRAINT `fk_alert_log_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 130 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '统一告警日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asin
-- ----------------------------
DROP TABLE IF EXISTS `asin`;
CREATE TABLE `asin`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `site` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '自定义昵称/备注',
  `inventory_threshold` int NULL DEFAULT NULL COMMENT '库存预警阈值',
  `brand` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '品牌名称(用于区分同组内不同品牌竞品)',
  `group_id` bigint NULL DEFAULT NULL COMMENT '所属分组ID(asin_group.id)',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_asin_site`(`asin` ASC, `site` ASC) USING BTREE,
  INDEX `idx_asin_group_id`(`group_id` ASC) USING BTREE,
  CONSTRAINT `fk_asin_group` FOREIGN KEY (`group_id`) REFERENCES `asin_group` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '监控目标ASIN管理表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asin_costs
-- ----------------------------
DROP TABLE IF EXISTS `asin_costs`;
CREATE TABLE `asin_costs`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联ASIN表主键ID',
  `purchase_cost` decimal(12, 4) NULL DEFAULT NULL,
  `shipping_cost` decimal(12, 4) NULL DEFAULT NULL,
  `fba_fee` decimal(12, 4) NULL DEFAULT NULL,
  `tariff_rate` decimal(6, 4) NULL DEFAULT NULL,
  `other_cost` decimal(12, 4) NULL DEFAULT NULL,
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_asin_costs_asin_id`(`asin_id` ASC) USING BTREE COMMENT '每个ASIN只能有一条成本配置',
  CONSTRAINT `fk_asin_costs_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'ASIN成本与利润计算配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for asin_group
-- ----------------------------
DROP TABLE IF EXISTS `asin_group`;
CREATE TABLE `asin_group`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分组描述/备注',
  `asin_count` int NULL DEFAULT 0 COMMENT '分组内ASIN数量（冗余字段，当前不自动维护）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_group_name`(`name` ASC) USING BTREE COMMENT '分组名称唯一索引'
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'ASIN 业务分组表：将多个竞品 ASIN 归类到同一自有产品的竞品池' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for asin_keywords
-- ----------------------------
DROP TABLE IF EXISTS `asin_keywords`;
CREATE TABLE `asin_keywords` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `asin_id` bigint NOT NULL COMMENT '关联ASIN表主键ID',
    `keyword` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关键词',
    `is_tracked` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否追踪排名 (1:是, 0:否)',
    `created_at` datetime(6) NOT NULL COMMENT '创建时间',
    `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ux_asin_keyword` (`asin_id`, `keyword`),
    CONSTRAINT `fk_asin_keywords_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V2.1 ASIN关键词表';


-- ----------------------------
-- Table structure for keyword_rank_history
-- ----------------------------
DROP TABLE IF EXISTS `keyword_rank_history`;
CREATE TABLE `keyword_rank_history` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `asin_keyword_id` bigint NOT NULL COMMENT '关联 asin_keywords.id',
    `scrape_date` date NOT NULL COMMENT '抓取日期',
    `natural_rank` int NOT NULL DEFAULT '-1' COMMENT '自然排名, -1表示未找到',
    `sponsored_rank` int NOT NULL DEFAULT '-1' COMMENT '广告排名, -1表示未找到',
    `page` int NOT NULL DEFAULT '-1' COMMENT '排名所在页数, -1表示未找到',
    `created_at` datetime(6) NOT NULL COMMENT '创建时间',
    `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_keyword_rank_history_date_asin` (`scrape_date`, `asin_keyword_id`),
    CONSTRAINT `fk_rank_history_keyword` FOREIGN KEY (`asin_keyword_id`) REFERENCES `asin_keywords` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V2.1 关键词排名历史记录表';

-- ----------------------------
-- Table structure for asin_history
-- ----------------------------
DROP TABLE IF EXISTS `asin_history`;
CREATE TABLE `asin_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `title` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '商品标题',
  `price` decimal(14, 2) NULL DEFAULT NULL COMMENT '价格',
  `bsr` int NULL DEFAULT NULL COMMENT 'BSR 排名',
  `bsr_category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BSR 大类',
  `bsr_subcategory` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'BSR 小类',
  `bsr_subcategory_rank` int NULL DEFAULT NULL COMMENT 'BSR 小类排名',
  `inventory` int NULL DEFAULT NULL COMMENT '库存快照',
  `bullet_points` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '五点要点 (换行分隔)',
  `image_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主图 MD5',
  `aplus_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'A+ 内容 MD5',
  `total_reviews` int NULL DEFAULT NULL COMMENT '总评论数',
  `avg_rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '平均评分',
  `snapshot_at` datetime(6) NOT NULL COMMENT '快照时间',
  `latest_negative_review_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最近差评 MD5 (用于变化检测)',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  `coupon_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '优惠券面额(例如 $10 off 或 5%)',
  `is_lightning_deal` tinyint(1) NULL DEFAULT 0 COMMENT '是否正在进行秒杀活动(1=是, 0=否)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_asin_history_asin_id`(`asin_id` ASC) USING BTREE,
  INDEX `idx_asin_history_snapshot_at`(`snapshot_at` ASC) USING BTREE,
  CONSTRAINT `fk_asin_history_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 237 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'ASIN 监控数据历史快照' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for change_alert
-- ----------------------------
DROP TABLE IF EXISTS `change_alert`;
CREATE TABLE `change_alert`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `alert_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变化类型 (TITLE / MAIN_IMAGE / BULLET_POINTS / APLUS_CONTENT / NEGATIVE_REVIEW ...)',
  `old_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `new_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_change_alert_asin_type`(`asin_id` ASC, `alert_type` ASC) USING BTREE,
  CONSTRAINT `fk_change_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 93 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通用字段变化记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for price_alert
-- ----------------------------
DROP TABLE IF EXISTS `price_alert`;
CREATE TABLE `price_alert`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `old_price` decimal(14, 2) NULL DEFAULT NULL COMMENT '旧价格',
  `new_price` decimal(14, 2) NULL DEFAULT NULL COMMENT '新价格',
  `change_percent` decimal(5, 2) NULL DEFAULT NULL COMMENT '变化百分比',
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `old_title` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `new_title` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `old_image_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '旧主图 MD5',
  `new_image_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '新主图 MD5',
  `old_bullet_points` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `new_bullet_points` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `old_aplus_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '旧 A+ MD5',
  `new_aplus_md5` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '新 A+ MD5',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_price_alert_asin_date`(`asin_id` ASC, `alert_at` ASC) USING BTREE,
  CONSTRAINT `fk_price_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 48 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '价格变动记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for review_alert
-- ----------------------------
DROP TABLE IF EXISTS `review_alert`;
CREATE TABLE `review_alert`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `review_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论唯一标识',
  `rating` int NOT NULL COMMENT '评分 1-5 星',
  `review_date` date NOT NULL COMMENT '评论日期',
  `review_text` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_review_id`(`review_id` ASC) USING BTREE,
  INDEX `idx_review_alert_asin_rating`(`asin_id` ASC, `rating` ASC) USING BTREE,
  CONSTRAINT `fk_review_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '差评监控告警记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for scrape_task
-- ----------------------------
DROP TABLE IF EXISTS `scrape_task`;
CREATE TABLE `scrape_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态 PENDING/RUNNING/SUCCESS/FAILED',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '执行结果或错误信息',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `run_at` datetime(6) NOT NULL COMMENT '调度执行时间',
  `finished_at` datetime(6) NULL DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scrape_task_asin_id`(`asin_id` ASC) USING BTREE,
  INDEX `idx_scrape_task_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_scrape_task_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 187 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '抓取任务执行记录表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
