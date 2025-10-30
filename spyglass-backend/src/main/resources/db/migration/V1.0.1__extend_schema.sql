-- V1.0.1: 扩展字段以支持新功能
-- 添加五点要点、差评MD5、评论数、评分、任务完成时间等字段

-- 为 asin_history 表添加新字段
ALTER TABLE asin_history 
ADD COLUMN IF NOT EXISTS bullet_points TEXT COMMENT '五点要点描述',
ADD COLUMN IF NOT EXISTS latest_negative_review_md5 VARCHAR(32) COMMENT '最新差评MD5',
ADD COLUMN IF NOT EXISTS total_reviews INT COMMENT '评论总数',
ADD COLUMN IF NOT EXISTS avg_rating DECIMAL(3,2) COMMENT '平均评分';

-- 为 scrape_task 表添加完成时间字段
ALTER TABLE scrape_task
ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP NULL COMMENT '任务完成时间';

-- 添加索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_asin_history_bullet_points ON asin_history(bullet_points(100));
CREATE INDEX IF NOT EXISTS idx_asin_history_negative_review ON asin_history(latest_negative_review_md5);
CREATE INDEX IF NOT EXISTS idx_scrape_task_finished_at ON scrape_task(finished_at);
