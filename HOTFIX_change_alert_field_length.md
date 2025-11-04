# 紧急修复：change_alert 表字段长度问题

## 问题描述
生产日志显示错误：
```
SQL Error: 1406, SQLState: 22001
Data truncation: Data too long for column 'new_value' at row 1
```

**影响**：五点描述、标题等长文本字段变更无法记录到 `change_alert` 表。

## 根本原因
`change_alert` 表的 `old_value` 和 `new_value` 字段可能是 `VARCHAR(255)` 类型，无法存储平均长度 900+ 字符的五点描述（BULLET_POINTS）。

## 立即修复步骤（生产环境）

### 1. 连接生产数据库
```bash
mysql -u spyglass -p -h shcamz.xyz spyglass
```

### 2. 检查当前字段类型
```sql
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'change_alert' 
  AND COLUMN_NAME IN ('old_value', 'new_value');
```

**如果输出显示 `varchar(255)` 或其他固定长度，继续下一步。**

### 3. 执行字段扩展（低峰期执行）
```sql
ALTER TABLE change_alert 
    MODIFY COLUMN old_value TEXT COMMENT '变更前的值';

ALTER TABLE change_alert 
    MODIFY COLUMN new_value TEXT COMMENT '变更后的值';
```

**预计耗时**：<1 秒（表数据量小）

### 4. 验证修复结果
```sql
SELECT COLUMN_NAME, COLUMN_TYPE 
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'change_alert' 
  AND COLUMN_NAME IN ('old_value', 'new_value');
```

**预期输出**：
```
+--------------+-------------+
| COLUMN_NAME  | COLUMN_TYPE |
+--------------+-------------+
| old_value    | text        |
| new_value    | text        |
+--------------+-------------+
```

### 5. 重启应用或等待下次抓取
```bash
# 方式1：手动触发单次抓取验证
curl -X POST http://shcamz.xyz:8081/api/debug/scrape/2

# 方式2：等待定时任务（每天UTC凌晨4点或应用启动10秒后）
```

### 6. 检查修复效果
```sql
-- 查看 change_alert 表是否有新记录
SELECT alert_type, COUNT(*) as count, MAX(alert_at) as latest
FROM change_alert 
GROUP BY alert_type;

-- 查看最新的五点变更记录（验证长文本存储）
SELECT id, alert_type, 
       LEFT(old_value, 50) as old_preview, 
       LEFT(new_value, 50) as new_preview,
       LENGTH(old_value) as old_len,
       LENGTH(new_value) as new_len,
       alert_at
FROM change_alert 
WHERE alert_type = 'BULLET_POINTS'
ORDER BY alert_at DESC 
LIMIT 5;
```

## 预期结果
修复后，所有字段变更（标题、主图、五点、A+、差评）都会正常记录到 `change_alert` 表。

## 后续自动化
新部署的环境会自动应用 Flyway 迁移脚本 `V1.0.2__fix_change_alert_value_length.sql`，无需手动操作。

## 回滚方案（如需）
```sql
-- 仅在确认不需要存储长文本时执行
ALTER TABLE change_alert 
    MODIFY COLUMN old_value VARCHAR(500);
ALTER TABLE change_alert 
    MODIFY COLUMN new_value VARCHAR(500);
```

## 验证日志关键字
修复后观察应用日志，应看到：
```
[Alert cid=xxx] BULLET_POINTS CHANGE recorded ASIN=xxx Old='...' New='...'
```

不再出现：
```
Data truncation: Data too long for column 'new_value'
```
