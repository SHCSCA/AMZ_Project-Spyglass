# 🚀 立即执行：生产环境部署和修复

## ⚡ 快速执行步骤（推荐）

### 在生产服务器上执行以下命令：

```bash
# 1. SSH 登录生产服务器
ssh root@shcamz.xyz

# 2. 进入项目目录
cd /root/AMZ_Project-Spyglass  # 或您实际的项目路径

# 3. 拉取最新代码
git pull origin dev

# 4. 重新构建并启动
docker-compose down && docker-compose up --build -d

# 5. 等待 30 秒让应用完全启动
sleep 30

# 6. 检查健康状态
curl -s http://localhost:8081/actuator/health | jq .

# 7. 查看当前表结构
curl -s http://localhost:8081/api/debug/change-alert/info | jq '.columns[] | select(.name=="old_value" or .name=="new_value")'

# 8. 执行数据库字段修复
curl -s -X POST http://localhost:8081/api/debug/change-alert/fix-field-length | jq .

# 9. 验证修复结果
curl -s http://localhost:8081/api/debug/change-alert/info | jq '.columns[] | select(.name=="old_value" or .name=="new_value")'
```

### 预期输出（修复前）：
```json
{
  "name": "old_value",
  "type": "tinytext",
  "maxLength": 255
}
{
  "name": "new_value",
  "type": "tinytext",
  "maxLength": 255
}
```

### 预期输出（修复后）：
```json
{
  "name": "old_value",
  "type": "text",
  "maxLength": 65535
}
{
  "name": "new_value",
  "type": "text",
  "maxLength": 65535
}
```

## 🧪 验证修复效果

### 1. 触发一次抓取
```bash
curl -X POST http://shcamz.xyz:8081/api/debug/scrape/2
```

### 2. 等待 10 秒后查看 change_alert 表
```bash
sleep 10
curl -s http://shcamz.xyz:8081/api/debug/change-alert/info | jq '{totalRecords, countByType, recentRecords: .recentRecords[:2]}'
```

### 预期结果：
```json
{
  "totalRecords": 4,
  "countByType": {
    "BULLET_POINTS": 1,
    "MAIN_IMAGE": 1,
    "TITLE": 1,
    "APLUS_CONTENT": 1
  },
  "recentRecords": [
    {
      "id": 1,
      "asinId": 2,
      "alertType": "BULLET_POINTS",
      "oldLength": 940,
      "newLength": 946,
      "oldPreview": "【Timeless Modern Design】: Clean lines...",
      "newPreview": "【Timeless Modern Design】: Clean lines...",
      "alertAt": "2025-11-05T10:56:00.000+00:00"
    }
  ]
}
```

### 3. 查看应用日志（验证无错误）
```bash
docker-compose logs -f app | grep -E "Alert|CHANGE|Error" | tail -50
```

**不应该再看到**：
- ❌ `Data truncation: Data too long for column 'new_value'`

**应该看到**：
- ✅ `[Alert cid=xxx] BULLET_POINTS CHANGE recorded ASIN=xxx`
- ✅ `[Alert cid=xxx] MAIN_IMAGE CHANGE recorded ASIN=xxx`

## 📊 监控持续运行

### 定时任务验证
应用会在以下时间自动抓取：
1. 启动后 10 秒（已执行）
2. 每天 UTC 凌晨 4:00（北京时间中午 12:00）

### 查看下次抓取时间
```bash
docker-compose logs app | grep "批量调度" | tail -5
```

## 🔄 如果需要手动回滚

```sql
-- 仅在确认需要回滚时执行（通常不需要）
ALTER TABLE change_alert MODIFY COLUMN old_value TINYTEXT;
ALTER TABLE change_alert MODIFY COLUMN new_value TINYTEXT;
```

## ✅ 完成检查清单

- [ ] 代码已部署到生产服务器
- [ ] 应用健康检查通过（`/actuator/health` 返回 `UP`）
- [ ] 数据库字段已修复（`TEXT` 类型，65535 字节）
- [ ] 触发测试抓取成功
- [ ] `change_alert` 表有新记录
- [ ] 日志无 "Data too long" 错误
- [ ] 日志级别已降低到 INFO

## 🆘 如遇到问题

### 问题 1：部署失败
```bash
# 查看详细错误
docker-compose logs app | tail -100
```

### 问题 2：修复端点返回 404
说明新版本未部署成功，重新执行步骤 3-4

### 问题 3：数据库连接失败
检查 docker-compose.yml 中的数据库配置环境变量

### 问题 4：修复后仍然报错
1. 确认字段类型已改为 TEXT（不是 TINYTEXT）
2. 重启应用：`docker-compose restart app`
3. 查看完整错误日志

---

**本次修复说明**：
- ✅ 新增自动修复端点：`POST /api/debug/change-alert/fix-field-length`
- ✅ 日志级别降低：DEBUG → INFO
- ✅ 修复字段类型：TINYTEXT (255字节) → TEXT (65535字节)
- ✅ 支持存储完整五点描述（约 900+ 字符）

**修复后变化**：
- change_alert 表将开始正常记录所有字段变更
- 日志量会减少（INFO 级别）
- 抓取时不再出现字段截断错误
