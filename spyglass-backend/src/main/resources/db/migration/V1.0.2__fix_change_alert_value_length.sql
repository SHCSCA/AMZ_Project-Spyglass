-- V1.0.2 修复 change_alert 表字段长度问题
-- 创建时间：2025-11-04
-- 描述：将 old_value 和 new_value 从 VARCHAR 扩展为 TEXT 类型，
--      解决五点描述等长文本字段存储时 "Data too long" 错误

-- 修改 old_value 字段为 TEXT（支持 65535 字节）
ALTER TABLE change_alert 
    MODIFY COLUMN old_value TEXT COMMENT '变更前的值 (支持长文本如五点描述、标题等)';

-- 修改 new_value 字段为 TEXT（支持 65535 字节）
ALTER TABLE change_alert 
    MODIFY COLUMN new_value TEXT COMMENT '变更后的值 (支持长文本如五点描述、标题等)';
