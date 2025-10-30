-- V1.0.0 测试环境初始化表结构 (H2 兼容版本)
-- 创建时间：2025-10-30
-- 说明：此文件是 V1.0.0__init.sql 的 H2 兼容版本，移除了 MySQL 特有的语法

-- ASIN 表
CREATE TABLE IF NOT EXISTS asin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin VARCHAR(64) NOT NULL,
    site VARCHAR(16) NOT NULL,
    nickname VARCHAR(255),
    inventory_threshold INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_asin_site UNIQUE (asin, site)
);

-- Asin 历史快照表
CREATE TABLE IF NOT EXISTS asin_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    title TEXT,
    price DECIMAL(14,2),
    bsr INT,
    inventory INT,
    bullet_points TEXT,
    image_md5 VARCHAR(64),
    latest_negative_review_md5 VARCHAR(64),
    aplus_md5 VARCHAR(64),
    total_reviews INT,
    avg_rating DECIMAL(2,1),
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asin_history_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_asin_history_asin_id ON asin_history(asin_id);
CREATE INDEX idx_asin_history_snapshot_at ON asin_history(snapshot_at);

-- 抓取任务表
CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT,
    status VARCHAR(32),
    message TEXT,
    retry_count INT DEFAULT 0,
    run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scrape_task_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE INDEX idx_scrape_task_asin_id ON scrape_task(asin_id);
CREATE INDEX idx_scrape_task_status ON scrape_task(status);

-- 评论监控表
CREATE TABLE IF NOT EXISTS review_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    review_id VARCHAR(128) NOT NULL,
    rating INT NOT NULL,
    review_date DATE NOT NULL,
    review_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_alert_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ux_review_id UNIQUE (review_id)
);
CREATE INDEX idx_review_alert_asin_rating ON review_alert(asin_id, rating);

-- 价格变动记录表
CREATE TABLE IF NOT EXISTS price_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    old_price DECIMAL(14,2),
    new_price DECIMAL(14,2),
    change_percent DECIMAL(5,2),
    alert_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_price_alert_asin FOREIGN KEY (asin_id) 
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX idx_price_alert_asin_date ON price_alert(asin_id, alert_at);