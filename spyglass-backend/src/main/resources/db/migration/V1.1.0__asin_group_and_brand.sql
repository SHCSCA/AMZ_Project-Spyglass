-- V1.1.0 新增分组与品牌字段迁移
-- 创建日期：2025-11-02
-- 说明：
--  1. 新增 asin_group 表，用于对多个竞品 ASIN 进行业务分组（一个自有产品对应多个竞品集合）
--  2. 在 asin 表中新增 brand 与 group_id 列，实现品牌区分与分组归属
--  3. 预留索引：后续若需要按品牌与分组高频过滤，可添加组合索引 (group_id, brand)

-- 1. 创建 asin_group 分组表
CREATE TABLE IF NOT EXISTS asin_group (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(128) NOT NULL COMMENT '分组名称',
    description VARCHAR(512) COMMENT '分组描述/备注',
    asin_count INT DEFAULT 0 COMMENT '分组内ASIN数量（冗余字段，当前不自动维护）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY ux_group_name (name) COMMENT '分组名称唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='ASIN 业务分组表：将多个竞品 ASIN 归类到同一自有产品的竞品池';

-- 2. asin 表新增品牌与分组外键列
ALTER TABLE asin
    ADD COLUMN brand VARCHAR(128) COMMENT '品牌名称(用于区分同组内不同品牌竞品)' AFTER inventory_threshold,
    ADD COLUMN group_id BIGINT COMMENT '所属分组ID(asin_group.id)' AFTER brand;

-- 3. 为分组外键添加索引（品牌暂不建索引，等查询场景明确后再加）
ALTER TABLE asin
    ADD INDEX idx_asin_group_id (group_id);

-- 4. 分组外键约束：删除分组时置空引用（不级联删除 ASIN）
ALTER TABLE asin
    ADD CONSTRAINT fk_asin_group FOREIGN KEY (group_id)
        REFERENCES asin_group(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- 回滚策略建议（手动执行）：
--  DROP FOREIGN KEY fk_asin_group; ALTER TABLE asin DROP COLUMN brand, DROP COLUMN group_id; DROP TABLE asin_group;
