-- AMZ Spyglass 生产数据库 Schema
-- 数据库: amzspaglass
-- 字符集: utf8mb4_unicode_ci
-- 引擎: InnoDB
-- 生成时间: 2025-11-06

-- ============================================================
-- ASIN 业务分组表
-- ============================================================
CREATE TABLE `asin_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分组描述/备注',
  `asin_count` int DEFAULT '0' COMMENT '分组内ASIN数量（冗余字段，当前不自动维护）',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_group_name` (`name`) COMMENT '分组名称唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN 业务分组表：将多个竞品 ASIN 归类到同一自有产品的竞品池';

-- ============================================================
-- 监控目标 ASIN 管理表
-- ============================================================
CREATE TABLE `asin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `site` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自定义昵称/备注',
  `inventory_threshold` int DEFAULT NULL COMMENT '库存预警阈值',
  `brand` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品牌名称(用于区分同组内不同品牌竞品)',
  `group_id` bigint DEFAULT NULL COMMENT '所属分组ID(asin_group.id)',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_asin_site` (`asin`,`site`),
  KEY `idx_asin_group_id` (`group_id`),
  CONSTRAINT `fk_asin_group` FOREIGN KEY (`group_id`) REFERENCES `asin_group` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控目标ASIN管理表';

-- ============================================================
-- ASIN 监控数据历史快照
-- ============================================================
CREATE TABLE `asin_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `title` text COLLATE utf8mb4_unicode_ci COMMENT '商品标题',
  `price` decimal(14,2) DEFAULT NULL COMMENT '价格',
  `bsr` int DEFAULT NULL COMMENT 'BSR 排名',
  `bsr_category` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BSR 大类',
  `bsr_subcategory` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BSR 小类',
  `bsr_subcategory_rank` int DEFAULT NULL COMMENT 'BSR 小类排名',
  `inventory` int DEFAULT NULL COMMENT '库存快照',
  `bullet_points` text COLLATE utf8mb4_unicode_ci COMMENT '五点要点 (换行分隔)',
  `image_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主图 MD5',
  `aplus_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'A+ 内容 MD5',
  `total_reviews` int DEFAULT NULL COMMENT '总评论数',
  `avg_rating` decimal(2,1) DEFAULT NULL COMMENT '平均评分',
  `snapshot_at` datetime(6) NOT NULL COMMENT '快照时间',
  `latest_negative_review_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近差评 MD5 (用于变化检测)',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_asin_history_asin_id` (`asin_id`),
  KEY `idx_asin_history_snapshot_at` (`snapshot_at`),
  CONSTRAINT `fk_asin_history_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN 监控数据历史快照';

-- ============================================================
-- 价格变动记录表
-- ============================================================
CREATE TABLE `price_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `old_price` decimal(14,2) DEFAULT NULL COMMENT '旧价格',
  `new_price` decimal(14,2) DEFAULT NULL COMMENT '新价格',
  `change_percent` decimal(5,2) DEFAULT NULL COMMENT '变化百分比',
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `old_title` mediumtext COLLATE utf8mb4_unicode_ci,
  `new_title` mediumtext COLLATE utf8mb4_unicode_ci,
  `old_image_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧主图 MD5',
  `new_image_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '新主图 MD5',
  `old_bullet_points` mediumtext COLLATE utf8mb4_unicode_ci,
  `new_bullet_points` mediumtext COLLATE utf8mb4_unicode_ci,
  `old_aplus_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧 A+ MD5',
  `new_aplus_md5` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '新 A+ MD5',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_price_alert_asin_date` (`asin_id`,`alert_at`),
  CONSTRAINT `fk_price_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价格变动记录表';

-- ============================================================
-- 通用字段变化记录表
-- ============================================================
CREATE TABLE `change_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `alert_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变化类型 (TITLE / MAIN_IMAGE / BULLET_POINTS / APLUS_CONTENT / NEGATIVE_REVIEW ...)',
  `old_value` text COLLATE utf8mb4_unicode_ci,
  `new_value` text COLLATE utf8mb4_unicode_ci,
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_change_alert_asin_type` (`asin_id`,`alert_type`),
  CONSTRAINT `fk_change_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用字段变化记录表';

-- ============================================================
-- 差评监控告警记录表
-- ============================================================
CREATE TABLE `review_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `review_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论唯一标识',
  `rating` int NOT NULL COMMENT '评分 1-5 星',
  `review_date` date NOT NULL COMMENT '评论日期',
  `review_text` tinytext COLLATE utf8mb4_unicode_ci,
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_review_id` (`review_id`),
  KEY `idx_review_alert_asin_rating` (`asin_id`,`rating`),
  CONSTRAINT `fk_review_alert_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='差评监控告警记录表';

-- ============================================================
-- 统一告警日志表
-- ============================================================
CREATE TABLE `alert_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `asin_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ASIN 字符串本体',
  `site` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '站点',
  `alert_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '告警类型',
  `severity` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '严重级别: INFO/WARN/CRITICAL',
  `alert_at` datetime(6) NOT NULL COMMENT '告警时间',
  `old_value` text COLLATE utf8mb4_unicode_ci,
  `new_value` text COLLATE utf8mb4_unicode_ci,
  `change_percent` decimal(8,2) DEFAULT NULL COMMENT '价格变化百分比(仅价格告警使用)',
  `ref_id` bigint DEFAULT NULL COMMENT '关联原始告警表记录 ID (price_alert/change_alert)',
  `context_json` json DEFAULT NULL COMMENT '结构化上下文 JSON',
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_alert_log_asin_type` (`asin_id`,`alert_type`),
  CONSTRAINT `fk_alert_log_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一告警日志表';

-- ============================================================
-- 抓取任务执行记录表
-- ============================================================
CREATE TABLE `scrape_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态 PENDING/RUNNING/SUCCESS/FAILED',
  `message` text COLLATE utf8mb4_unicode_ci COMMENT '执行结果或错误信息',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `run_at` datetime(6) NOT NULL COMMENT '调度执行时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scrape_task_asin_id` (`asin_id`),
  KEY `idx_scrape_task_status` (`status`),
  CONSTRAINT `fk_scrape_task_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抓取任务执行记录表';

-- 表名：asin_keywords
-- 说明：ASIN 关键词监控配置表（按 asin_id 关联）
CREATE TABLE `asin_keywords` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `keyword` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需要监控的关键词',
  `is_tracked` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用监控 1=是 0=否',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_asin_keywords_asin_keyword` (`asin_id`,`keyword`),
  KEY `idx_asin_keywords_asin_id` (`asin_id`),
  CONSTRAINT `fk_asin_keywords_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN 关键词监控配置表（按 asin_id 关联）';

-- 表名：asin_costs
-- 说明：ASIN 成本与利润配置表（按 asin_id 关联）
CREATE TABLE `asin_costs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联 asin.id',
  `purchase_cost` decimal(12,4) DEFAULT NULL COMMENT '采购成本（美元）',
  `shipping_cost` decimal(12,4) DEFAULT NULL COMMENT '头程运费（美元）',
  `fba_fee` decimal(12,4) DEFAULT NULL COMMENT 'FBA 配送费（美元）',
  `tariff_rate` decimal(6,4) DEFAULT NULL COMMENT '关税税率，例如 0.0600 = 6%',
  `other_cost` decimal(12,4) DEFAULT NULL COMMENT '其他杂项费用',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_asin_costs_asin_id` (`asin_id`) COMMENT '每个 ASIN 仅允许一条成本配置',
  CONSTRAINT `fk_asin_costs_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN 成本与利润配置表（按 asin_id 关联）';