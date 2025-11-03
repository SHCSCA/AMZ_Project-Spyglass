-- 完整数据库结构快照 (截至 2025-11-02)
-- 组合自迁移脚本 V1.0.0, V1.0.1, V1.1.0
-- 仅供快速初始化 / 文档参考，生产请使用逐步 Flyway 迁移，勿随意合并已上线历史版本。

/*
  表清单：
    asin                - 监控目标ASIN基础信息 + 品牌 + 分组外键
    asin_group          - ASIN业务分组
    asin_history        - 历史快照
    scrape_task         - 抓取任务执行记录
    review_alert        - 差评监控记录
    price_alert         - 价格变动记录
    change_alert        - 通用字段变更告警（标题/主图/五点/A+）
*/

CREATE TABLE IF NOT EXISTS asin (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin VARCHAR(64) NOT NULL COMMENT 'Amazon 标准识别号',
    site VARCHAR(16) NOT NULL COMMENT '站点代码(US/UK等)',
    nickname VARCHAR(255) COMMENT '自定义昵称/备注',
    inventory_threshold INT COMMENT '库存预警阈值',
    brand VARCHAR(128) COMMENT '品牌名称(用于区分同组内不同品牌竞品)',
    group_id BIGINT COMMENT '所属分组ID(asin_group.id)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    UNIQUE KEY ux_asin_site (asin, site) COMMENT 'ASIN+站点唯一索引',
    INDEX idx_asin_group_id (group_id),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控目标ASIN管理表';

CREATE TABLE IF NOT EXISTS asin_group (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(128) NOT NULL COMMENT '分组名称',
    description VARCHAR(512) COMMENT '分组描述/备注',
    asin_count INT DEFAULT 0 COMMENT '分组内ASIN数量(冗余字段)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_group_name (name) COMMENT '分组名称唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN业务分组表';

ALTER TABLE asin
    ADD CONSTRAINT fk_asin_group FOREIGN KEY (group_id)
        REFERENCES asin_group(id) ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE IF NOT EXISTS asin_history (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    title TEXT COMMENT '商品标题',
    price DECIMAL(14,2) COMMENT '当前价格',
    bsr INT COMMENT 'Best Seller Rank排名',
    inventory INT COMMENT '预估库存数量',
    bullet_points TEXT COMMENT '商品五点要点(抓取顺序换行存储)',
    image_md5 VARCHAR(64) COMMENT '主图MD5哈希值',
    latest_negative_review_md5 VARCHAR(64) COMMENT '最新差评的MD5哈希值',
    aplus_md5 VARCHAR(64) COMMENT 'A+页面内容MD5哈希值',
    total_reviews INT COMMENT '总评论数',
    avg_rating DECIMAL(2,1) COMMENT '平均评分',
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_asin_history_asin_id (asin_id),
    INDEX idx_asin_history_snapshot_at (snapshot_at),
    PRIMARY KEY (id),
    CONSTRAINT fk_asin_history_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASIN监控数据历史快照表';

CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT COMMENT '关联的asin表ID',
    status VARCHAR(32) COMMENT '任务状态(PENDING/RUNNING/SUCCESS/FAILED)',
    message TEXT COMMENT '执行结果消息/错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_scrape_task_asin_id (asin_id),
    INDEX idx_scrape_task_status (status),
    PRIMARY KEY (id),
    CONSTRAINT fk_scrape_task_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抓取任务执行记录表';

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
    INDEX idx_review_alert_asin_rating (asin_id, rating) COMMENT 'ASIN+评分联合索引',
    CONSTRAINT fk_review_alert_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='差评监控记录表';

CREATE TABLE IF NOT EXISTS price_alert (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    old_price DECIMAL(14,2) COMMENT '变动前价格',
    new_price DECIMAL(14,2) COMMENT '变动后价格',
    change_percent DECIMAL(5,2) COMMENT '价格变动百分比',
    alert_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    INDEX idx_price_alert_asin_date (asin_id, alert_at) COMMENT 'ASIN+告警时间联合索引',
    CONSTRAINT fk_price_alert_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价格变动记录表';

CREATE TABLE IF NOT EXISTS change_alert (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    alert_type VARCHAR(64) NOT NULL COMMENT '告警类型(TITLE, MAIN_IMAGE, BULLET_POINTS, APLUS_CONTENT)',
    old_value TEXT COMMENT '变更前的值(旧标题或旧内容MD5)',
    new_value TEXT COMMENT '变更后的值(新标题或新内容MD5)',
    alert_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    INDEX idx_change_alert_asin_type_date (asin_id, alert_type, alert_at) COMMENT 'ASIN+类型+告警时间联合索引',
    CONSTRAINT fk_change_alert_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用字段变更告警记录表';
