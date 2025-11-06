-- V1.1.2 统一修复告警相关表文本字段类型，防止回退为 TINYTEXT 造成截断
-- 目标：保证所有旧/新值与 message 字段均为 TEXT (至多 64KB)；如需更大可后续升级为 MEDIUMTEXT。
-- 允许重复执行，MySQL MODIFY 语句幂等（若已是 TEXT 则不改变）。

ALTER TABLE change_alert 
    MODIFY COLUMN old_value TEXT COMMENT '变更前的值',
    MODIFY COLUMN new_value TEXT COMMENT '变更后的值';

ALTER TABLE alert_log 
    MODIFY COLUMN old_value TEXT COMMENT '旧值预览',
    MODIFY COLUMN new_value TEXT COMMENT '新值预览',
    MODIFY COLUMN message TEXT COMMENT '告警描述';

-- 可选：如后续需要保存更长 HTML / A+ 内容，可再升级为 MEDIUMTEXT (~16MB)
-- ALTER TABLE change_alert MODIFY COLUMN old_value MEDIUMTEXT; -- 示例
