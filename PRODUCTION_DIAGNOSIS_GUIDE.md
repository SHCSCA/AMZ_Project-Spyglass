# 生产环境诊断与修复指南

## 📋 当前状态

已添加新的调试端点 `/api/debug/change-alert/info`，用于远程查询 `change_alert` 表的结构和数据。

## 🔄 部署步骤

### 1. 在生产服务器上拉取最新代码

```bash
# SSH 登录到生产服务器
ssh user@shcamz.xyz

# 进入项目目录
cd /path/to/AMZ_Project-Spyglass

# 拉取最新 dev 分支代码
git pull origin dev

# 重新构建并启动容器
docker-compose down
docker-compose up --build -d

# 查看启动日志
docker-compose logs -f app
```

### 2. 等待应用完全启动

等待约 30-60 秒，直到看到日志：
```
[Scheduler] 开始批量调度抓取任务（启动时立即执行 + 每天UTC凌晨4点）...
```

## 🔍 诊断数据库状态

### 方式 1：使用新的调试端点（推荐）

```bash
# 查询 change_alert 表的完整信息
curl -s http://shcamz.xyz:8081/api/debug/change-alert/info | jq .
```

**预期输出解析**：

```json
{
  "columns": [
    {
      "name": "id",
      "type": "bigint",
      "maxLength": null,
      "nullable": "NO"
    },
    {
      "name": "old_value",
      "type": "varchar(255)",  // ❌ 如果是这个，需要修复！
      "maxLength": 255,
      "nullable": "YES"
    },
    {
      "name": "new_value",
      "type": "text",          // ✅ 应该是这个
      "maxLength": 65535,
      "nullable": "YES"
    }
  ],
  "totalRecords": 0,           // 当前记录数
  "countByType": {},           // 按类型分组统计
  "recentRecords": [],         // 最近5条记录
  "success": true
}
```

### 方式 2：直接连接数据库（需要密码）

```bash
# 从 docker-compose 环境变量或 .env 文件获取密码
mysql -h shcamz.xyz -P 3306 -u spyglass -p spyglass

# 查询表结构
SHOW CREATE TABLE change_alert;

# 查询字段类型
DESC change_alert;
```

## 🛠️ 执行修复（如果需要）

### 如果诊断显示字段类型不是 TEXT

在生产数据库执行以下 SQL：

```sql
USE spyglass;

-- 检查当前类型
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'change_alert' 
  AND COLUMN_NAME IN ('old_value', 'new_value');

-- 修复字段类型（低峰期执行）
ALTER TABLE change_alert 
    MODIFY COLUMN old_value TEXT COMMENT '变更前的值';

ALTER TABLE change_alert 
    MODIFY COLUMN new_value TEXT COMMENT '变更后的值';

-- 验证修复结果
DESC change_alert;
```

## ✅ 验证修复效果

### 1. 手动触发一次抓取

```bash
# 触发 ASIN ID=2 的抓取
curl -X POST http://shcamz.xyz:8081/api/debug/scrape/2
```

### 2. 等待 5-10 秒后查看 change_alert 表

```bash
# 查看最新数据
curl -s http://shcamz.xyz:8081/api/debug/change-alert/info | jq '.recentRecords'
```

**预期看到**：

```json
[
  {
    "id": 1,
    "asinId": 2,
    "alertType": "BULLET_POINTS",
    "oldLength": 940,         // 五点描述长度
    "newLength": 946,
    "oldPreview": "【Timeless Modern Design】: Clean lines...",
    "newPreview": "【Timeless Modern Design】: Clean lines...",
    "alertAt": "2025-11-05T10:45:15.000+00:00"
  },
  {
    "id": 2,
    "asinId": 2,
    "alertType": "MAIN_IMAGE",
    "oldLength": 32,
    "newLength": 32,
    "oldPreview": "8b82ce595561d81f38d753baf00b630e",
    "newPreview": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "alertAt": "2025-11-05T10:45:15.000+00:00"
  }
]
```

### 3. 查看统计数据

```bash
curl -s http://shcamz.xyz:8081/api/debug/change-alert/info | jq '{totalRecords, countByType}'
```

**预期输出**：

```json
{
  "totalRecords": 5,
  "countByType": {
    "TITLE": 1,
    "MAIN_IMAGE": 1,
    "BULLET_POINTS": 2,
    "APLUS_CONTENT": 1
  }
}
```

## 📊 监控数据变化

### 查看所有历史快照

```bash
# 查询 asin_history 表数据量
curl -s http://shcamz.xyz:8081/api/debug/asin-count
# 输出: asin_count=2

# 查看 ASIN 列表
curl -s http://shcamz.xyz:8081/api/debug/asin/list | jq .
```

### 实时查看应用日志

```bash
# 在生产服务器上
docker-compose logs -f app | grep -E "Alert|Scheduler|Task"
```

**关键日志标记**：

✅ 正常运行：
```
[Alert cid=xxx] BULLET_POINTS CHANGE recorded ASIN=xxx Old='...' New='...'
[Alert cid=xxx] PRICE-CHANGE recorded ASIN=xxx Old=32.99 New=34.99
```

❌ 需要修复：
```
Data truncation: Data too long for column 'new_value'
```

## 🔄 定时任务验证

应用启动后会立即执行一次抓取（10秒后），之后每天 UTC 凌晨 4:00 自动运行。

```bash
# 查看下次执行时间（观察日志）
docker-compose logs app | grep "批量调度"
```

## 📝 总结

1. **部署新版本** → 拉取代码 + docker-compose up --build -d
2. **查询表结构** → curl /api/debug/change-alert/info
3. **执行 SQL 修复** → ALTER TABLE ... MODIFY COLUMN ... TEXT
4. **触发抓取验证** → curl -X POST /api/debug/scrape/2
5. **检查结果** → curl /api/debug/change-alert/info

如有任何问题，查看日志：
```bash
docker-compose logs -f app
```
