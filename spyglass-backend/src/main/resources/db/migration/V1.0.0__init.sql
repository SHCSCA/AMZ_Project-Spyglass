-- V1.0.0 初始化表结构（中文注释）
-- 说明：此迁移脚本创建核心表（asin/asin_history/scrape_task）并添加索引与注释
-- Flyway 命名约定：V1.0.0__init.sql，两个下划线

-- ASIN 表：保存要监控的 ASIN 元数据
CREATE TABLE IF NOT EXISTS asin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin VARCHAR(64) NOT NULL,
    site VARCHAR(16) NOT NULL,
    nickname VARCHAR(255),
    inventory_threshold INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY ux_asin_site (asin, site)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Asin 历史快照表：保存每次抓取的快照
CREATE TABLE IF NOT EXISTS asin_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT NOT NULL,
    title TEXT,
    price DECIMAL(14,2),
    bsr INT,
    inventory INT,
    image_md5 VARCHAR(64),
    aplus_md5 VARCHAR(64),
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_asin_history_asin_id (asin_id),
    INDEX idx_asin_history_snapshot_at (snapshot_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE asin_history
  ADD CONSTRAINT fk_asin_history_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE CASCADE;

-- 抓取任务表：记录每次抓取任务的状态与日志
CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asin_id BIGINT,
    status VARCHAR(32),
    message TEXT,
    run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scrape_task_asin_id (asin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE scrape_task
  ADD CONSTRAINT fk_scrape_task_asin FOREIGN KEY (asin_id) REFERENCES asin(id) ON DELETE SET NULL;