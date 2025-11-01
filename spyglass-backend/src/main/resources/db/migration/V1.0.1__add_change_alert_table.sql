-- V1.0.1 增加通用变更告警表
-- 创建时间：2025-11-01
-- 描述：此脚本创建 change_alert 表，用于记录除价格和差评外的其他重要字段变更，
--      如标题、主图、五点描述、A+内容的变更。

CREATE TABLE IF NOT EXISTS change_alert (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    asin_id BIGINT NOT NULL COMMENT '关联的asin表ID',
    alert_type VARCHAR(64) NOT NULL COMMENT '告警类型 (TITLE, MAIN_IMAGE, BULLET_POINTS, APLUS_CONTENT)',
    old_value TEXT COMMENT '变更前的值 (例如: 旧标题或旧内容的MD5)',
    new_value TEXT COMMENT '变更后的值 (例如: 新标题或新内容的MD5)',
    alert_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    INDEX idx_change_alert_asin_type_date (asin_id, alert_type, alert_at) COMMENT 'ASIN、类型和告警时间联合索引',
    CONSTRAINT fk_change_alert_asin FOREIGN KEY (asin_id)
        REFERENCES asin(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='通用字段变更告警记录表';
