-- V1.0.0: 初始数据库结构
-- 创建基础表结构

-- ASIN 主表
CREATE TABLE IF NOT EXISTS asin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin VARCHAR(20) NOT NULL UNIQUE,
    nickname VARCHAR(255),
    site VARCHAR(10) NOT NULL DEFAULT 'US',
    inventory_threshold INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ASIN 历史快照表
CREATE TABLE IF NOT EXISTS asin_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    title TEXT,
    price DECIMAL(10,2),
    bsr INT,
    inventory INT,
    image_md5 VARCHAR(32),
    aplus_md5 VARCHAR(32),
    snapshot_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_asin_history_asin_id ON asin_history(asin_id);
CREATE INDEX idx_asin_history_snapshot_at ON asin_history(snapshot_at);

-- 抓取任务表
CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    run_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_scrape_task_asin_id ON scrape_task(asin_id);
CREATE INDEX idx_scrape_task_status ON scrape_task(status);

-- 价格告警表
CREATE TABLE IF NOT EXISTS price_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    old_price DECIMAL(10,2),
    new_price DECIMAL(10,2),
    change_percent DECIMAL(5,2),
    alert_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_price_alert_asin_id ON price_alert(asin_id);
CREATE INDEX idx_price_alert_alert_at ON price_alert(alert_at);
