-- V1.0.0 初始化表结构（中文注释）
-- 说明：此迁移脚本创建核心表（asin/asin_history/scrape_task）并添加索引与注释
-- Flyway 命名约定：V1.0.0__init.sql，两个下划线

-- ASIN 表：保存要监控的 ASIN 元数据
CREATE TABLE IF NOT EXISTS asin (
    id BIGSERIAL PRIMARY KEY,
    asin VARCHAR(64) NOT NULL,
    site VARCHAR(16) NOT NULL,
    nickname VARCHAR(255),
    inventory_threshold INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE (asin, site)
);

-- 添加中文注释（字段说明）
COMMENT ON TABLE asin IS '要监控的 ASIN 元数据表';
COMMENT ON COLUMN asin.id IS '主键，自增 ID';
COMMENT ON COLUMN asin.asin IS 'Amazon 商品 ASIN 标识';
COMMENT ON COLUMN asin.site IS '站点代码，例如 US/UK/DE';
COMMENT ON COLUMN asin.nickname IS '用户定义的昵称或备注';
COMMENT ON COLUMN asin.inventory_threshold IS '库存告警阈值，小于该值触发告警';
COMMENT ON COLUMN asin.created_at IS '记录创建时间';
COMMENT ON COLUMN asin.updated_at IS '记录最后更新时间';

-- Asin 历史快照表：保存每次抓取的快照
CREATE TABLE IF NOT EXISTS asin_history (
    id BIGSERIAL PRIMARY KEY,
    asin_id BIGINT NOT NULL REFERENCES asin(id) ON DELETE CASCADE,
    title TEXT,
    price NUMERIC(14,2),
    bsr INTEGER,
    inventory INTEGER,
    image_md5 VARCHAR(64),
    aplus_md5 VARCHAR(64),
    snapshot_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_asin_history_asin_id ON asin_history(asin_id);
CREATE INDEX IF NOT EXISTS idx_asin_history_snapshot_at ON asin_history(snapshot_at DESC);

-- 中文注释：asin_history 表和字段说明
COMMENT ON TABLE asin_history IS 'ASIN 抓取历史快照表，记录每次抓取的关键数据';
COMMENT ON COLUMN asin_history.id IS '主键，自增 ID';
COMMENT ON COLUMN asin_history.asin_id IS '关联 asin 表的外键';
COMMENT ON COLUMN asin_history.title IS '抓取到的商品标题';
COMMENT ON COLUMN asin_history.price IS '抓取到的价格（数值）';
COMMENT ON COLUMN asin_history.bsr IS '抓取到的 BSR（类目排名）';
COMMENT ON COLUMN asin_history.inventory IS '抓取到的库存快照（可为空）';
COMMENT ON COLUMN asin_history.image_md5 IS '主图 MD5（真实图片二进制 MD5 或 URL MD5）';
COMMENT ON COLUMN asin_history.aplus_md5 IS 'A+ 内容的 MD5（用于变更检测）';
COMMENT ON COLUMN asin_history.snapshot_at IS '抓取时间戳';

-- 抓取任务表：记录每次抓取任务的状态与日志
CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGSERIAL PRIMARY KEY,
    asin_id BIGINT, -- 可能为空（批量任务或通用任务）
    status VARCHAR(32), -- RUNNING / SUCCESS / FAILED
    message TEXT,
    run_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_scrape_task_asin_id ON scrape_task(asin_id);

-- 中文注释：scrape_task 表和字段说明
COMMENT ON TABLE scrape_task IS '抓取任务记录表，记录任务状态与消息日志';
COMMENT ON COLUMN scrape_task.id IS '主键，自增 ID';
COMMENT ON COLUMN scrape_task.asin_id IS '关联 ASIN 的 ID（可为空）';
COMMENT ON COLUMN scrape_task.status IS '任务状态，例如 RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN scrape_task.message IS '任务执行过程中的简要消息或错误信息';
COMMENT ON COLUMN scrape_task.run_at IS '任务触发/执行时间';