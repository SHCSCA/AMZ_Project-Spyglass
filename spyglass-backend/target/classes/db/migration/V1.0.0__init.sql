-- V1.0.0 初始化表结构
-- 创建时间：2025-10-29
-- 描述：此迁移脚本创建亚马逊竞品监控系统的核心表结构，包括：
--   1. asin: 监控目标管理表
--   2. asin_history: 监控数据历史快照表
--   3. scrape_task: 抓取任务执行记录表
--   4. review_alert: 差评监控记录表
--   5. price_alert: 价格变动记录表

-- ASIN 表：保存要监控的 ASIN 基础信息
CREATE TABLE IF NOT EXISTS asin (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin VARCHAR(64) NOT NULL COMMENT 'Amazon 标准识别号',
    site VARCHAR(16) NOT NULL COMMENT '站点代码(US/UK等)',
    nickname VARCHAR(255) COMMENT '自定义昵称/备注',
    inventory_threshold INT COMMENT '库存预警阈值',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    UNIQUE KEY ux_asin_site (asin, site) COMMENT 'ASIN和站点组合唯一索引',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='监控目标ASIN管理表';

-- Asin 历史快照表：记录每次抓取的数据快照
CREATE TABLE IF NOT EXISTS asin_history (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    title TEXT COMMENT '商品标题',
    price DECIMAL(14,2) COMMENT '当前价格',
    bsr INT COMMENT 'Best Seller Rank排名',
    inventory INT COMMENT '预估库存数量',
    bullet_points TEXT COMMENT '商品五点要点（feature bullets），多行文本，按抓取顺序换行存储',
    image_md5 VARCHAR(64) COMMENT '主图MD5哈希值',
    aplus_md5 VARCHAR(64) COMMENT 'A+页面内容MD5哈希值',
    total_reviews INT COMMENT '总评论数',
    avg_rating DECIMAL(2,1) COMMENT '平均评分',
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_asin_history_asin_id (asin_id) COMMENT 'ASIN ID索引',
    INDEX idx_asin_history_snapshot_at (snapshot_at) COMMENT '快照时间索引',
    PRIMARY KEY (id),
    CONSTRAINT fk_asin_history_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='ASIN监控数据历史快照表';

-- 抓取任务表：记录每次抓取任务的执行状态
CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT COMMENT '关联的asin表ID',
    status VARCHAR(32) COMMENT '任务状态(PENDING/RUNNING/SUCCESS/FAILED)',
    message TEXT COMMENT '执行结果消息/错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_scrape_task_asin_id (asin_id) COMMENT 'ASIN ID索引',
    INDEX idx_scrape_task_status (status) COMMENT '任务状态索引',
    PRIMARY KEY (id),
    CONSTRAINT fk_scrape_task_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='抓取任务执行记录表';

-- 评论监控表：记录新增的差评信息
CREATE TABLE IF NOT EXISTS review_alert (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    review_id VARCHAR(128) NOT NULL COMMENT '评论唯一标识',
    rating INT NOT NULL COMMENT '评分(1-5星)',
    review_date DATE NOT NULL COMMENT '评论日期',
    review_text TEXT COMMENT '评论内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_review_id (review_id) COMMENT '评论ID唯一索引',
    INDEX idx_review_alert_asin_rating (asin_id, rating) COMMENT 'ASIN和评分联合索引',
    CONSTRAINT fk_review_alert_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='差评监控记录表';

-- 价格变动记录表：记录显著的价格变动
CREATE TABLE IF NOT EXISTS price_alert (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    old_price DECIMAL(14,2) COMMENT '变动前价格',
    new_price DECIMAL(14,2) COMMENT '变动后价格',
    change_percent DECIMAL(5,2) COMMENT '价格变动百分比',
    alert_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    INDEX idx_price_alert_asin_date (asin_id, alert_at) COMMENT 'ASIN和告警时间联合索引',
    CONSTRAINT fk_price_alert_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='价格变动记录表';