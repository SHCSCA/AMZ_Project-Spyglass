-- 紧急修复脚本：修复 change_alert 表字段长度问题
-- 执行时机：立即在生产数据库执行
-- 影响：修改表结构，建议在低峰期执行
-- 执行方式：mysql -u用户名 -p密码 数据库名 < hotfix_change_alert_value_length.sql

USE spyglass;

-- 检查当前字段类型
SELECT 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'spyglass' 
  AND TABLE_NAME = 'change_alert'
  AND COLUMN_NAME IN ('old_value', 'new_value');

-- 修改字段类型为 TEXT（最大 65,535 字节，足以存储五点描述等内容）
ALTER TABLE change_alert 
    MODIFY COLUMN old_value TEXT COMMENT '变更前的值 (支持长文本如五点描述、标题等)';

ALTER TABLE change_alert 
    MODIFY COLUMN new_value TEXT COMMENT '变更后的值 (支持长文本如五点描述、标题等)';

-- 验证修改结果
SELECT 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'spyglass' 
  AND TABLE_NAME = 'change_alert'
  AND COLUMN_NAME IN ('old_value', 'new_value');

-- 预期输出：
-- old_value | text | 65535
-- new_value | text | 65535
