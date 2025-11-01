-- Spyglass MySQL 建表脚本 (统一版)
-- 说明: 此脚本基于当前 Hibernate 映射 + 业务需求整理。
-- 特点: 使用 utf8mb4 + 注释 + 明确唯一/外键约束。保留所有告警相关扩展表。
-- 使用方式: 在全新 MySQL 数据库中执行一次完成初始化。后续结构变更建议通过迁移工具维护 (Flyway/Liquibase)。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================
-- 表: asin (监控目标 ASIN 基础信息)
-- =============================
DROP TABLE IF EXISTS `asin`;
CREATE TABLE `asin` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin` VARCHAR(64) NOT NULL COMMENT 'Amazon 标准识别号',
  `site` VARCHAR(16) NOT NULL COMMENT '站点代码 (US/UK/DE 等)',
  `nickname` VARCHAR(255) NULL DEFAULT NULL COMMENT '自定义昵称/备注',
  `inventory_threshold` INT NULL DEFAULT NULL COMMENT '库存预警阈值',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_asin_site` (`asin`, `site`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控目标ASIN管理表';

-- =============================
-- 表: asin_history (每次抓取快照)
-- =============================
DROP TABLE IF EXISTS `asin_history`;
CREATE TABLE `asin_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` BIGINT NOT NULL COMMENT '关联 asin.id',
  `title` TEXT NULL COMMENT '商品标题',
  `price` DECIMAL(14,2) NULL COMMENT '价格',
  `bsr` INT NULL COMMENT 'BSR 排名',
  `bsr_category` VARCHAR(255) NULL COMMENT 'BSR 大类',
  `bsr_subcategory` VARCHAR(255) NULL COMMENT 'BSR 小类',
  `bsr_subcategory_rank` INT NULL COMMENT 'BSR 小类排名',
  `inventory` INT NULL COMMENT '库存快照',
  `bullet_points` TEXT NULL COMMENT '五点要点 (换行分隔)',
  `image_md5` VARCHAR(64) NULL COMMENT '主图 MD5',
  `aplus_md5` VARCHAR(64) NULL COMMENT 'A+ 内容 MD5',
  `total_reviews` INT NULL COMMENT '总评论数',
  `avg_rating` DECIMAL(2,1) NULL COMMENT '平均评分',
  `snapshot_at` DATETIME(6) NOT NULL COMMENT '快照时间',
  `latest_negative_review_md5` VARCHAR(64) NULL COMMENT '最近差评 MD5 (用于变化检测)',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_asin_history_asin_id` (`asin_id`),
  KEY `idx_asin_history_snapshot_at` (`snapshot_at`),
  CONSTRAINT `fk_asin_history_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN 监控数据历史快照';

-- =============================
-- 表: price_alert (价格变化 + 额外上下文)
-- =============================
DROP TABLE IF EXISTS `price_alert`;
CREATE TABLE `price_alert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` BIGINT NOT NULL COMMENT '关联 asin.id',
  `old_price` DECIMAL(14,2) NULL COMMENT '旧价格',
  `new_price` DECIMAL(14,2) NULL COMMENT '新价格',
  `change_percent` DECIMAL(5,2) NULL COMMENT '变化百分比',
  `alert_at` DATETIME(6) NOT NULL COMMENT '告警时间',
  `old_title` TEXT NULL COMMENT '旧标题',
  `new_title` TEXT NULL COMMENT '新标题',
  `old_image_md5` VARCHAR(64) NULL COMMENT '旧主图 MD5',
  `new_image_md5` VARCHAR(64) NULL COMMENT '新主图 MD5',
  `old_bullet_points` TEXT NULL COMMENT '旧五点',
  `new_bullet_points` TEXT NULL COMMENT '新五点',
  `old_aplus_md5` VARCHAR(64) NULL COMMENT '旧 A+ MD5',
  `new_aplus_md5` VARCHAR(64) NULL COMMENT '新 A+ MD5',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_price_alert_asin_date` (`asin_id`, `alert_at`),
  CONSTRAINT `fk_price_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价格变动记录表';

-- =============================
-- 表: change_alert (单字段变化记录)
-- =============================
DROP TABLE IF EXISTS `change_alert`;
CREATE TABLE `change_alert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` BIGINT NOT NULL COMMENT '关联 asin.id',
  `alert_type` VARCHAR(64) NOT NULL COMMENT '变化类型 (TITLE / MAIN_IMAGE / BULLET_POINTS / APLUS_CONTENT / NEGATIVE_REVIEW ...)',
  `old_value` TEXT NULL COMMENT '旧值',
  `new_value` TEXT NULL COMMENT '新值',
  `alert_at` DATETIME(6) NOT NULL COMMENT '告警时间',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_change_alert_asin_type` (`asin_id`, `alert_type`),
  CONSTRAINT `fk_change_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用字段变化记录表';

-- =============================
-- 表: review_alert (差评新增监控记录)
-- =============================
DROP TABLE IF EXISTS `review_alert`;
CREATE TABLE `review_alert` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` BIGINT NOT NULL COMMENT '关联 asin.id',
  `review_id` VARCHAR(128) NOT NULL COMMENT '评论唯一标识',
  `rating` INT NOT NULL COMMENT '评分 1-5 星',
  `review_date` DATE NOT NULL COMMENT '评论日期',
  `review_text` TEXT NULL COMMENT '评论内容',
  `alert_at` DATETIME(6) NOT NULL COMMENT '告警时间',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_review_id` (`review_id`),
  KEY `idx_review_alert_asin_rating` (`asin_id`, `rating`),
  CONSTRAINT `fk_review_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='差评监控告警记录表';

-- =============================
-- 表: alert_log (统一告警日志 - 价格/字段变化聚合)
-- =============================
DROP TABLE IF EXISTS `alert_log`;
CREATE TABLE `alert_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` BIGINT NOT NULL COMMENT '关联 asin.id',
  `asin_code` VARCHAR(32) NOT NULL COMMENT 'ASIN 字符串本体',
  `site` VARCHAR(16) NOT NULL COMMENT '站点',
  `alert_type` VARCHAR(64) NOT NULL COMMENT '告警类型',
  `severity` VARCHAR(32) NULL COMMENT '严重级别: INFO/WARN/CRITICAL',
  `alert_at` DATETIME(6) NOT NULL COMMENT '告警时间',
  `old_value` TEXT NULL COMMENT '旧值',
  `new_value` TEXT NULL COMMENT '新值',
  `change_percent` DECIMAL(8,2) NULL COMMENT '价格变化百分比(仅价格告警使用)',
  `ref_id` BIGINT NULL COMMENT '关联原始告警表记录 ID (price_alert/change_alert)',
  `context_json` JSON NULL COMMENT '结构化上下文 JSON',
  `message` TEXT NULL COMMENT '人类可读描述',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_alert_log_asin_type` (`asin_id`, `alert_type`),
  CONSTRAINT `fk_alert_log_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一告警日志表';

-- =============================
-- 表: scrape_task (抓取任务执行记录)
-- =============================
DROP TABLE IF EXISTS `scrape_task`;
CREATE TABLE `scrape_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` BIGINT NOT NULL COMMENT '关联 asin.id',
  `status` VARCHAR(32) NOT NULL COMMENT '任务状态 PENDING/RUNNING/SUCCESS/FAILED',
  `message` TEXT NULL COMMENT '执行结果或错误信息',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `run_at` DATETIME(6) NOT NULL COMMENT '调度执行时间',
  `finished_at` DATETIME(6) NULL COMMENT '完成时间',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scrape_task_asin_id` (`asin_id`),
  KEY `idx_scrape_task_status` (`status`),
  CONSTRAINT `fk_scrape_task_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抓取任务执行记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- 结束
