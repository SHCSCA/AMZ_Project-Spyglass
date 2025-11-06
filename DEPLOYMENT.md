# AMZ Spyglass - 亚马逊竞品情报系统

## 生产环境部署指南

### 系统架构

- **后端**: Spring Boot 3.5.0 + Java 21
- **数据库**: MySQL 8.0.36
- **调度**: Spring Scheduler (每日 UTC 04:00 + 启动时执行)
- **爬虫**: HttpClient + Jsoup + Selenium (三层策略)
- **API**: RESTful + OpenAPI/Swagger UI

### 快速部署

#### 1. 环境要求

- Java 21+
- Maven 3.6+
- MySQL 8.0+

#### 2. 数据库初始化

```bash
# 导入schema
mysql -h <host> -u <user> -p <database> < schema.sql
```

#### 3. 配置环境变量

```bash
export DB_HOST=your-mysql-host
export DB_PORT=3306
export DB_NAME=amzspaglass
export DB_USER=your-user
export DB_PASSWORD=your-password
```

#### 4. 启动应用

```bash
cd spyglass-backend
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

或使用 Docker:

```bash
docker-compose up -d
```

#### 5. 访问接口

- **API 文档**: http://localhost:8081/swagger-ui.html
- **健康检查**: http://localhost:8081/actuator/health

### API 端点

#### ASIN 管理
- `POST /api/asins` - 添加监控ASIN
- `GET /api/asins` - 查询ASIN列表
- `GET /api/asins/{id}` - 查询单个ASIN
- `PUT /api/asins/{id}` - 更新ASIN配置
- `DELETE /api/asins/{id}` - 删除ASIN

#### 历史数据
- `GET /api/asin-history/{asinId}` - 查询ASIN历史快照

#### 告警日志
- `GET /api/alerts/latest` - 查询最新告警
- `GET /api/alerts/asin/{asinId}` - 查询指定ASIN告警

#### 分组管理
- `POST /api/groups` - 创建ASIN分组
- `GET /api/groups` - 查询分组列表

### 调度任务

系统会在以下时机自动抓取:
1. 应用启动后 10 秒（初始化抓取）
2. 每日 UTC 04:00（定时抓取）

### 告警触发规则

系统会自动检测并记录以下变化:

1. **价格变化** - 记录到 `price_alert` 和 `alert_log`
2. **标题变化** - 记录到 `change_alert` (TITLE)
3. **主图变化** - 记录到 `change_alert` (MAIN_IMAGE)
4. **五点要点变化** - 记录到 `change_alert` (BULLET_POINTS)
5. **A+内容变化** - 记录到 `change_alert` (APLUS_CONTENT)
6. **新差评** - 记录到 `change_alert` (NEGATIVE_REVIEW)

### 数据库表结构

详见 `schema.sql` 文件，主要表:

- `asin` - ASIN监控目标
- `asin_group` - ASIN分组
- `asin_history` - 历史快照
- `price_alert` - 价格告警
- `change_alert` - 字段变化告警
- `review_alert` - 差评告警
- `alert_log` - 统一告警日志
- `scrape_task` - 抓取任务记录

### 配置说明

主要配置文件: `spyglass-backend/src/main/resources/application.yml`

关键配置项:
- `spring.datasource.*` - 数据库连接
- `scraper.proxy.*` - 代理配置
- `dingtalk.webhook.url` - 钉钉告警webhook

### 监控与运维

#### 健康检查
```bash
curl http://localhost:8081/actuator/health
```

#### 查看日志
```bash
tail -f spyglass-backend/logs/app.log
```

#### 数据备份
```bash
mysqldump -h <host> -u <user> -p <database> > backup_$(date +%Y%m%d).sql
```

### 故障排查

1. **应用无法启动** - 检查数据库连接和端口占用
2. **抓取失败** - 检查代理配置和网络连通性
3. **告警未触发** - 检查 `alert_log` 表是否有记录

### 技术支持

详细文档请参考项目 README.md

---

**版本**: 1.0.0  
**最后更新**: 2025-11-06
