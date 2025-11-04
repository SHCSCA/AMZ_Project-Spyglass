# 产品需求文档 (PRD): 亚马逊竞品情报系统 V2.0 (999加购法)

| 文档版本 | V2.0 (999加购法) |
| :--- | :--- |
| **项目名称** | 亚马逊竞品情报系统 (Project Spyglass) |
| **创建日期** | 2025年10月30日 |
| **修订日期** | 2025年11月01日 |
| **修订说明** | **V2.0: 基于V1.4版本升级。核心目标：1. 实现“999加购法”以抓取精准高位库存。 2. 实现差评内容的抓取、存储和告警推送。** |
| **目标** | 打造一个7x24小时运行的自动化竞品监控引擎，通过API驱动和AI辅助的UI，为**个人运营者**提供关键决策情报并解放生产力。 |

---

## 1. 简介

### 1.1. 问题陈述 (The Problem)
我（作为亚马逊运营）目前依赖**手动、重复**的劳动来跟踪竞品。这包括每天打开数十个ASIN页面，检查价格变动、新差评、BSR排名和库存情况。这个过程极其耗时、容易出错、缺乏数据沉淀，并且总是**被动响应**而非**主动预警**。

### 1.2. 解决方案 (The Solution)
“亚马逊竞品情报系统”是一个个人SaaS工具。它通过**自动化Web爬虫**，7x24小时抓取、存储和分析竞品数据。系统将通过一个**现代Web界面**（由AI辅助生成）展示历史趋势、变化对比，将警报**写入数据库** (`alert_log`)，并通过**钉钉**（可选）发送**主动警报**。

### 1.3. V2.0 核心目标 (Goals & Objectives)
1.  **效率提升：** 释放我每天1-2小时的手动跟踪时间。
2.  **主动决策：** 将我从“被动查看”转变为“**登录即知**”（通过DB警报）和“**主动收到警报**”（通过钉钉），在竞品行动（如调价、断货）的**第一时间**做出反应。
3.  **数据沉淀：** 建立竞品历史数据库 (`asin_history`)，用于复盘和战略分析。
4.  **情报深度 (V2.0)：** 实现精准的**高位库存**跟踪和**差评内容**的即时获取。

### 1.4. 范围 (Scope)

| V2.0 范围内 (In-Scope) | V2.0 范围外 (Out-of-Scope) |
| :--- | :--- |
| ✅ **三层Web爬虫策略** (HttpClient -> Jsoup -> Selenium) | ❌ **认证与安全 (F-MOD-1)** (V3.0 考虑) |
| ✅ 关键数据点（标题、五点、价格、BSR(全)、评论、A+、主图） | ❌ 任何官方亚马逊SP-API/MWS的集成 |
| ✅ **(V2.0) "999加购法"** 模拟库存抓取 | ❌ 自动化亚马逊后台操作（如自动调价） |
| ✅ **(V2.0) 差评内容** 抓取与存储 (`review_alert`) | ❌ **后端负责HTML Diff高亮** (移至前端实现) |
| ✅ 历史数据存储和图表化展示 (MySQL) | ❌ 抓取亚马逊以外的平台（如eBay, Walmart） |
| ✅ **数据库驱动的警报日志** (写入 `alert_log`) | ❌ **图片二进制MD5下载** |
| ✅ **(可选) 钉钉**主动警报 | ❌ 抓取关键词搜索排名 (V3.0 考虑) |
| ✅ API First 架构（Spring Boot RESTful API） | |
| ✅ AI辅助生成的Web前端（React/Vue） | |

---

## 1.5 快速开始 (Getting Started)

### 运行方式概览
你可以通过三种方式启动后端：
1. 本地直接运行（开发调试）
2. 使用 Docker Compose 一键编排（推荐生产/测试环境）
3. 仅启动后端 + 连接已有 MySQL（外部数据库）

### 依赖要求
| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 21 | Spring Boot 3.x 要求 |
| Maven | 3.9+ | 构建工具（仓库已包含 `pom.xml`） |
| MySQL | 8.x | 主数据存储 |
| 浏览器驱动 | Chrome 最新 / Headless | 用于 Selenium 高位库存与差评抓取 |
| 代理服务 | 住宅代理（Bright Data 等） | 必须，所有亚马逊请求经代理 |

### 目录结构速览
```
spyglass-backend/
	src/main/java/com/amz/spyglass/...
	src/main/resources/application.yml
docker-compose.yml
```

### 快速启动（Docker Compose，使用外部 MySQL）
1. 准备一个可访问的 MySQL 8 数据库，创建库：`CREATE DATABASE spyglass CHARACTER SET utf8mb4;`
2. 创建用户（如需要）：`CREATE USER 'spyuser'@'%' IDENTIFIED BY 'StrongPass!'; GRANT ALL ON spyglass.* TO 'spyuser'@'%';`
3. 在项目根目录创建 `.env` 文件：
```
DB_HOST=your.mysql.host
DB_PORT=3306
DB_NAME=spyglass
DB_USER=spyuser
DB_PASSWORD=StrongPass!
SCRAPER_FIXED_DELAY_MS=14400000
```
4. 启动：
```
git clone <repo>
cd AMZ_Project-Spyglass
docker compose --env-file .env up --build -d
```
5. 验证：
```
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8081/v3/api-docs | head -n 20
```
启动后：
* 后端：`http://localhost:8081`
* OpenAPI：`/swagger-ui/index.html` / `/v3/api-docs`
* 健康检查：`/actuator/health`

### 本地开发启动（不使用 Docker）
1. 安装并启动本地 MySQL：创建数据库 `spyglass`，编码 `utf8mb4`。
2. 修改 `application.yml` 中的 `spring.datasource.*` 为你的本地账户。
3. 运行：
```
cd spyglass-backend
mvn -DskipTests spring-boot:run
```
4. （可选）配置代理与钉钉：通过环境变量或在 `application.yml` 中临时填写（生产请用环境变量）。

### 必要环境变量（推荐通过 `.env` 或部署平台注入）
| 变量 | 示例值 | 用途 |
| --- | --- | --- |
| `SCRAPER_FIXED_DELAY_MS` | `14400000` | 全量抓取间隔（默认 4h） |
| `DB_HOST` / `DB_PORT` | `mysql` / `3306` | docker-compose 下由服务名解析 |
| `DB_NAME` | `spyglass` | 数据库名称 |
| `DB_USER` / `DB_PASS` | `root` / `password` | 数据库凭证（生产请替换） |
| `PROXY_HOST` / `PROXY_PORT` | `proxy.provider.com` / `12345` | 代理服务器 |
| `PROXY_USER` / `PROXY_PASS` | `user123` / `pass123` | 代理认证（所有请求经此） |
| `DINGTALK_WEBHOOK` | `https://oapi.dingtalk.com/robot/send?...` | 钉钉告警推送（可选） |
| `PORT` | `8081` | 后端监听端口（`server.port`），可覆盖默认 8081 |

> 端口说明：应用内使用 `server.port: ${PORT:8081}`，因此在 Docker/部署平台设置 `PORT=9090` 即可改为 9090，无需修改代码或重新打包。健康检查与文档中的所有示例也需同步调整。

> Hibernate 方言说明：已移除显式 `MySQL8Dialect`（Hibernate 6 中被弃用），现在由 Hibernate 自动检测 MySQL 版本；如需显式指定可使用 `org.hibernate.dialect.MySQLDialect`。

### 运行日志与转储
* 运行日志：`spyglass.log`（或 stdout，取决于 Logback 配置）。
* HTML 转储：当抓取失败或关键字段缺失时写入 `logs/html-dump/`，由 `HtmlDumpCleaner` 定期清理。

### 健康与监控
* 健康：`GET /actuator/health` 返回 `{"status":"UP"}`。
* 信息：`GET /actuator/info` 可扩展版本信息（可在 `application.yml` 添加 `info.build.version`）。
* 调度频率：通过环境变量 `SCRAPER_FIXED_DELAY_MS` 动态调整（无需重启容器）。

### 数据流简述
1. 定时调度器读取所有 ASIN。
2. 三层抓取（HttpClient -> Jsoup -> Selenium）获取页面内容。
3. `ScrapeParser` 解析字段 -> 得到结构化快照。
4. 持久化到 `asin_history`。
5. `AlertService` 对比上一次快照 -> 触发并写入 `alert_log` / 可选钉钉推送。
6. 新的差评写入 `review_alert`（并对 1-3 星差评触发告警）。

### 示例：添加与查询 ASIN（分页）
```
# 添加 ASIN（新增 brand 与 groupId 可选字段）
curl -X POST http://localhost:8081/api/asin \
	-H 'Content-Type: application/json' \
	-d '{"asin":"B0TEST1234","site":"US","nickname":"Test Product","inventoryThreshold":20,"brand":"Sagenest","groupId":1}'

# 查询列表
curl 'http://localhost:8081/api/asin?page=0&size=20'

# 查询历史（最近30天，分页第0页）
curl 'http://localhost:8081/api/asin/1/history?range=30d&page=0&size=100'
```

### 示例：警报与差评（分页与过滤）
```
# 获取最新警报
curl 'http://localhost:8081/api/alerts?page=0&size=50&type=PRICE_CHANGE'

# 获取某 ASIN 的警报
curl http://localhost:8081/api/asin/1/alerts

# 获取某 ASIN 的差评（仅 1-3 星）
curl 'http://localhost:8081/api/asin/1/reviews?rating=negative&page=0&size=50'

### 分页与查询参数说明
| 端点 | 参数 | 说明 |
| --- | --- | --- |
| `GET /api/asin` | `page` / `size` | ASIN 列表分页，按 id DESC 排序 |
| `GET /api/asin` | `groupId` | 按分组过滤（仅返回指定分组下的 ASIN） |
| `GET /api/asin` | （预留）`brand` | 未来可按品牌快速过滤（当前未实现，待场景明确） |
| `GET /api/asin/{id}/history` | `range` / `page` / `size` | 时间范围与分页（历史数据内部按 snapshotAt DESC）|
| `GET /api/alerts` | `page` / `size` / `type` | 最新告警分页，可按类型过滤 |
| `GET /api/asin/{id}/reviews` | `rating=negative` / `page` / `size` | 负面评论过滤（1-3 星）与分页 |
| `GET /api/asin/{id}/alerts` | `page` / `size` / `type` / `from` / `to` | 指定 ASIN 告警分页 + 类型与时间范围过滤 |

### 统一分页响应结构 (PageResponse)
所有支持分页的端点统一返回如下 JSON 结构（字段含义）：

```json
{
	"items": [ /* 当前页数据数组 */ ],
	"total": 1234,              // 符合条件的总记录数
	"page": 0,                  // 当前页码 (从0开始)
	"size": 50,                 // 每页请求的大小
	"totalPages": 25,           // 总页数 (基于 total 与 size 计算)
	"hasNext": true,            // 是否还有下一页 (page < totalPages-1)
	"hasPrevious": false        // 是否有上一页 (page > 0)
}
```

#### 分页参数约束与校验
* `size` 参数全局最大值为 **200**，超过会返回 HTTP 400，并输出标准错误响应。
* 所有分页端点均已使用 Bean Validation (`@Max(200)`) 以及业务级手动校验双重保障。
* 推荐前端在 UI 选择框限制可选值（10 / 20 / 50 / 100 / 200）。

#### 标准错误响应结构
服务端统一返回以下 JSON 结构（`GlobalExceptionHandler`）：

```json
{
	"error": "INVALID_PARAM",
	"message": "size 超过最大限制 200",
	"details": null,
	"timestamp": "2025-11-03T08:00:00Z"
}
```

错误码约定（节选）：
| error | 触发场景 |
| ----- | -------- |
| `INVALID_PARAM` | 参数校验失败 / 业务约束不满足（如 size>200） |
| `NOT_FOUND` | 资源不存在 |
| `INTERNAL_ERROR` | 未捕获的运行时异常 |
| `BAD_REQUEST` | JSON 解析、类型转换等输入格式错误 |

#### 时间与时区规范
* 所有返回的时间戳（`createdAt`, `updatedAt`, `snapshotAt`, `alertAt` 等）统一使用 **UTC**。
* 序列化格式：ISO-8601（例如：`2025-11-03T08:12:34Z`）。
* Jackson 在 `application.yml` 中已配置：`spring.jackson.time-zone: UTC`。

#### 价格变动字段 `change_percent`
* 数据库字段：`DECIMAL(8,2)`，以百分比数值形式表达涨跌幅。
* 含义：`(newPrice - oldPrice) / oldPrice * 100`。
* 正数表示上涨，负数表示下跌；例如：`-25.00` 表示下降 25%。
* 后端在生成告警时统一保留 **2 位小数**（四舍五入）。

#### 可空字段（OpenAPI 标注）
主要响应 DTO 已在 OpenAPI 中用 `@Schema(nullable = true)` 标明可能为 `null` 的字段，前端解析时需做缺省处理：
* 价格、库存、BSR 相关字段在首轮抓取或失败补全时可能为 `null`。
* A+ 内容、主图 MD5、Bullet Points 在未获取到 HTML 时为空。
* 评论相关的 `reviewText` / `rating` 解析失败时可能为 `null`。

#### 前端处理建议摘要
| 场景 | 建议策略 |
| ---- | -------- |
| 字段为 null | 使用占位符 `--` 或灰色标签显示 |
| 时间展示 | 统一本地转换为用户时区并相对时间（如“5分钟前”） |
| change_percent | 根据正负号着色（红/绿），绝对值 <0.01% 可显示 `<0.01%` |
| 分页翻页 | 直接使用响应中的 `hasNext` / `hasPrevious` |
| 错误提示 | 若 `error=INVALID_PARAM` 且 `message` 含 size，提示“分页大小上限 200” |


前端分页建议：
1. 使用 `hasNext` 与 `hasPrevious` 控制翻页按钮禁用状态。
2. 若需要“跳页”功能，使用 `totalPages` 生成页码选择器；大数据量时改为“输入页码 + Go”。
3. 若 `size` 可变，切换 size 后应重置 `page=0` 以避免越界。
4. 当 `total` 很大（> 10w）且需要深翻页，可改造后端为基于游标的分页（未来 Roadmap 可选）。

示例（获取某 ASIN 历史记录页）：

```bash
curl 'http://localhost:8081/api/asin/1/history?range=30d&page=0&size=100' | jq '.total,.totalPages,.hasNext'
```

### 使用外部数据库的注意事项
* 需要确保应用容器能够访问外部 MySQL（安全组 / 防火墙开放 3306）。
* 建议为生产环境开启只读账号与最小权限策略。
* 如果使用云数据库（RDS 等），启用自动备份与多可用区。 

### 前端跨域（CORS）配置说明
后端实现了动态多源跨域支持，可通过环境变量 `frontend.origins` 注入允许的多个 Origin，逗号分隔。默认已包含：`http://shcamz.xyz:8082,http://localhost:5173,http://localhost:8080,http://127.0.0.1:5173` 等常见开发 / 生产来源。

实现要点：
* `WebConfig` 使用 `@Value("${frontend.origins:...}")` 读取，拆分后注册到 `addCorsMappings` -> `/api/**`。
* 允许方法：GET, POST, PUT, PATCH, DELETE, OPTIONS；允许头：Authorization, Content-Type, Accept, X-Requested-With, Origin。
* `allowCredentials(true)` 已开启，前端需在 fetch/axios 中设置 `credentials: 'include'`（若未来加入 Cookie 会话或鉴权）。

配置方式示例：
```yaml
# application.yml（开发环境可放置；生产推荐用环境变量覆盖）
frontend:
	origins: http://shcamz.xyz:8082,http://localhost:5173
```

或在容器 / 启动参数中：
```bash
export frontend.origins="https://dash.company.com,http://localhost:5173"
```

注意事项：
1. 逗号分隔无空格；必须带协议；不要在末尾添加多余逗号。
2. 新的前端域名无法访问时，先用 curl 验证是否缺失在列表中。
3. 与 Nginx / 反向代理并用时，确保未覆盖或剥离 Origin 头。

验证（简单 GET 带 Origin）：
```bash
curl -i -H 'Origin: http://shcamz.xyz:8082' http://localhost:8081/api/debug/openapi/status | grep -i access-control-allow-origin
```
预期包含：`Access-Control-Allow-Origin: http://shcamz.xyz:8082`

预检（OPTIONS）示例：
```bash
curl -i -X OPTIONS \
	-H 'Origin: http://shcamz.xyz:8082' \
	-H 'Access-Control-Request-Method: GET' \
	http://localhost:8081/api/asin
```

排查表：
| 现象 | 类型 | 排查动作 | 解决 |
| --- | --- | --- | --- |
| 浏览器报 CORS 错误（网络面板无真正响应） | 预检失败 | 用上面 curl 带 Origin | 将域名加入 `frontend.origins` |
| 返回 500 且有后端统一错误 JSON | 业务异常 | 查看日志栈追踪 | 修复代码 / 数据问题 |
| OPTIONS 返回 403/404 | 路径或代理问题 | 对比本地成功示例 | 调整代理或 CORS 映射路径 |

扩展：后续可按 profile 拆分（dev 放宽，prod 收紧），或增加基于正则的域名过滤、统计来源访问频率等。

### 性能调优建议（可选）
| 场景 | 建议 |
| --- | --- |
| 历史数据量大 | 为 `asin_history(asin_id, snapshot_at)` 建复合索引 |
| 告警查询频繁 | 为 `alert_log(alert_type, alert_at)` 建索引 |
| 差评查询频繁 | 为 `review_alert(asin_id, rating)` 建索引 |
| 高并发 | 引入 Caffeine/Redis 缓存热点 ASIN 基础信息 |
| 分组 + 品牌过滤频繁 | 视查询需要补充复合索引 `(group_id, brand)`（当前仅对 group_id 建索引，品牌未建索引避免过早优化） |

### V1.1.0 迁移（分组与品牌）说明

本版本新增了“分组（asin_group）”与“品牌（asin.brand）”能力，使同一自有产品的多个竞品可以聚合与区分：

| 结构 | 新增字段/表 | 说明 |
| --- | --- | --- |
| 表 | `asin_group` | 分组元数据（名称、描述、asin_count 预留） |
| 列 | `asin.brand` | 品牌字符串，便于区分同分组内不同品牌 |
| 列 | `asin.group_id` | 外键引用分组，删除分组时自动置空 |
| 约束 | `fk_asin_group` | ON DELETE SET NULL，避免误删 ASIN |
| 索引 | `idx_asin_group_id` | 支持按分组分页与过滤 |

迁移文件：`V1.1.0__asin_group_and_brand.sql`

后续可选：
1. 添加 `(group_id, brand)` 复合索引（当品牌过滤大幅增加时）。
2. 维护 `asin_group.asin_count`（在新增/移除 ASIN 时更新）。
3. 增加品牌过滤参数 `GET /api/asin?brand=xxx`。

### 数据库结构与迁移策略（Schema Strategy）

> TL;DR：生产环境请始终保留按版本递增的迁移文件（Flyway `V*.sql`），`schema_full_YYYY-MM-DD.sql` 仅作为“参考快照 / 全量初始化”用途，不能替代历史迁移，否则会丢失增量演进记录。

当前项目采用“增量迁移 + 全量快照”双轨策略：

| 文件类型 | 位置 | 用途 | 生产环境执行 | 回滚/审计价值 |
| --- | --- | --- | --- | --- |
| 增量迁移 (`V1.0.0__*.sql`) | `src/main/resources/db/migration/` | 每次模型演进最小变更集 | ✅（Flyway 自动） | ✅（精确定位变更） |
| 全量快照 (`schema_full_2025-11-02.sql`) | `spyglass-backend/sql/` | 新环境一次性初始化 / 文档 | ⚠️（仅限空库一次性导入） | ❌（不含变更时间线） |

使用建议：
1. 本地或新测试环境：可直接执行最新 `schema_full_*.sql` 建库加速。后续继续使用增量迁移。  
2. 生产环境：严禁删除旧版 `V*.sql`；新增字段/索引/表时继续追加新的 `Vx.y.z__desc.sql`。  
3. 当迁移文件较多（>30）时，可考虑年终生成一个“归档快照”文件，仅用于文档或快速初始化，不纳入 Flyway baseline。  
4. 回滚策略：通过 Git 历史恢复上一个迁移文件 + 手工补偿（例如：DROP COLUMN / DROP TABLE），避免直接替换为快照。  
5. 审计与合规：保留增量迁移可在 CodeReview / 安全审计中逐条溯源每次结构变更的意图与日期。  

索引策略当前最小化（仅 `idx_asin_group_id`），后续根据负载再添加：
* 建议 `(group_id, brand)` 复合索引：在品牌+分组筛选高频时启用。  
* 历史曲线查询优化：`asin_history (asin_id, snapshot_at)` 复合索引（已在性能建议中列出）。  
* 告警与差评高频过滤：分别考虑 `alert_log (alert_type, alert_at)` 与 `review_alert (asin_id, rating)`。  

字段 MD5 对比策略（用于内容变更告警）：
| 领域 | 字段来源 | 对比方式 | 告警触发条件 |
| --- | --- | --- | --- |
| 主图 | `imageMd5` | 新旧 MD5 不同 | 生成 `CHANGE_IMAGE` 告警 |
| A+ 区域 | `aplusMd5` | 新旧 MD5 不同 | 生成 `CHANGE_APLUS` 告警 |
| 标题 | `title` (原文) | `MD5(title)` 变化 | 生成 `CHANGE_TITLE` |
| 五点描述 | `bulletPoints` (拼接) | `MD5(concat)` 变化 | 生成 `CHANGE_BULLETS` |
| 最新负面评论 | `latestNegativeReviewMd5` | 新旧 MD5 不同 | 生成 `NEW_NEGATIVE_REVIEW` |

如需进一步压缩迁移数量，可使用：
* Flyway Baseline：在引入已有历史库时设置 `flyway.baselineOnMigrate=true`。  
* 合并副本策略：仅在“长期不再回滚”且完成审计后，将早期（例如 <V1.0.x）的多文件打包为单一 `baseline` 文件。  

> 注意：本项目尚未启用 Flyway 自动执行（可在生产前开启）；合并迁移需谨慎，务必保留历史演进证据避免运维风险。


### 分组相关 API 示例

创建分组：
```bash
curl -X POST http://localhost:8081/api/groups \
	-H 'Content-Type: application/json' \
	-d '{"name":"50寸L桌竞品","description":"与我家L桌直接竞争的同尺寸款"}'
```

查询分组列表（分页）：
```bash
curl 'http://localhost:8081/api/groups?page=0&size=20'
```

按分组查看 ASIN：
```bash
curl 'http://localhost:8081/api/groups/1/asins?page=0&size=50'
```

按分组过滤全局 ASIN 列表：
```bash
curl 'http://localhost:8081/api/asin?page=0&size=50&groupId=1'
```


### 常见问题 (FAQ)
| 问题 | 说明 | 解决方案 |
| --- | --- | --- |
| 健康检查失败 | `/actuator/health` 返回 DOWN | 检查数据库连接与代理配置是否正确 |
| 抓取总是失败 | 被反爬/代理失效 | 更换住宅代理出口或降低抓取频率 |
| 库存始终为 99 | Selenium 未正确执行加购 | 检查浏览器驱动与页面结构是否变化 |
| 差评未触发告警 | 评论解析规则变化 | 调整 `ScrapeParser` 中评论区选择器 |
| 钉钉无消息 | Webhook 未配置或被限流 | 确认环境变量并查看响应日志 |

### 升级与扩展建议
* 添加 `AlertController` / `ReviewController` 使警报和差评查询 API 落地。
* 引入缓存（Caffeine/Redis）减少重复页面抓取（未来 V3.0）。
* 增加代理池轮换与失败自动切换。
* 添加抓取性能指标（抓取耗时、失败率）到 `/actuator/metrics`（需 Micrometer）。

### 安全注意
当前版本无认证（假设私有网络）。若需公网访问：
1. Nginx 层加 Basic Auth / IP 白名单。
2. 启用 HTTPS。
3. 后端后续版本引入 JWT（V3.0 规划）。

---

---

## 2. 用户画像与核心场景 (User Personas & Stories)

* **画像1：Alex（我，亚马逊运营兼开发者）**
    * **痛点：** “我每天早上要花1小时开20个竞品Tab页，生怕他们降价或上了差评我不知道。我受够了复制粘贴到Excel里。”
    * **需求：** “我需要一个（跑在我的服务器上的）仪表盘，一眼就能看到所有竞品‘昨天发生了什么’。并且，如果他们有动作，立刻通知我！”

| 优先级 | 作为... (Persona) | 我想要... (Action) | 以便我能... (Goal) |
| :--- | :--- | :--- | :--- |
| **P0** | 我 (运营) | 访问仪表盘，列出我所有监控的ASIN | 快速了解大盘情况。 |
| **P0** | 我 (运营) | 在仪表盘上看到哪些ASIN**发生了变化**（如价格、差评） | 优先处理最重要的信息。 |
| **P0** | 我 (运营) | 添加一个新的竞品ASIN进行监控 | 扩展我的情报网络。 |
| **P0** | 我 (运营) | 当一个竞品**价格变动**或**库存低于阈值**时 | **立即**在我的**钉钉群**里收到**纯文本通知**。 |
| **P0** | 我 (运营) | 当竞品**标题或五点**发生变化时 | 在**钉钉**中收到**变更通知**（前端负责高亮对比）。 |
| **P1** | 我 (运营) | 当一个竞品**获得了新的1-3星差评**时 | **立即**在**钉钉**中收到通知，**并查看差评内容**，以便分析他们的弱点。 |
| **P1** | 我 (运营) | 点击任意一个ASIN，查看它的**历史价格和BSR曲线** | 分析它的长期策略和销售趋势。 |
| **P1** | 我 (开发) | 通过**API文档**（Swagger）来测试后端 | 确保后端按预期工作，并指导AI生成前端。 |
| **P2** | 我 (运营) | 收到竞品**主图或A+内容变更**的警报 | 了解他们是否在测试新的营销素材。 |
| **(V2.0) P1**| 我 (运营) | 查看竞品的**精准库存**（即使库存>100） | 准确判断对方的备货量和潜在断货风险。 |

---

## 3. 功能需求 (Functional Requirements)

### F-MOD-1: 认证 & 安全 (Auth)
* **F-AUTH-001 (V3.0):** (V3.0) 系统提供基于用户名和密码的登录功能。
* **(V1.0/V2.0 策略):** V1.0/V2.0 假设系统部署在**受信任的私有网络**中，不暴露于公网。

### F-MOD-2: ASIN管理 (Management)
* **F-ASIN-001 (仪表盘):** 用户访问UI应看到一个“ASIN列表”仪表盘。
* **F-ASIN-002 (列表项):** 列表中的每一项应展示ASIN的**最新关键数据**：名称、ASIN、主图、当前价格、当前BSR、评论数、**精准预估库存**。
* **F-ASIN-003 (警报标识):** 列表项必须有明显的**视觉标识**，表明该ASIN在最近一次抓取中是否触发了警报 (通过 F-API-005 查询)。
* **F-ASIN-004 (添加ASIN):** 用户必须能通过一个表单添加新的ASIN。
    * **输入字段：** ASIN (字符串), 站点 (下拉框: US, UK等), 昵称 (字符串), 库存警报阈值 (数字)。
* **F-ASIN-005 (删除ASIN):** 用户必须能从仪表盘中删除一个ASIN。
* **F-ASIN-006 (详情页):** 点击任意ASIN，应跳转到该ASIN的“详情页”。

### F-MOD-3: 爬虫引擎 (Scraper Engine) - (后端)
* **F-SCRAPE-000 (三层策略):** 系统必须采用三层抓取策略：
    1.  **HttpClientScraper** (优先)：使用 `httpclient5`，支持主动代理认证。
    2.  **JsoupScraper** (回退)：使用 `Jsoup` 进行静态HTML抓取。
    3.  **SeleniumScraper** (补充)：使用 `Selenium` 补充 `HttpClient/Jsoup` 无法获取的动态加载字段。
* **F-SCRAPE-001 (定时调度):** 系统必须有一个定时任务调度器（`@Scheduled`），按预设频率（例如：每4小时）自动为列表中的所有ASIN执行抓取。
* **F-SCRAPE-002 (异步执行):** 抓取任务必须是异步的（`@Async`），确保单个ASIN的抓取失败不会阻塞整个队列。
* **F-SCRAPE-003 (代理集成 - 关键):** **所有**对亚马逊的HTTP/S请求**必须**通过 `ProxyManager` 经由**旋转住宅IP代理服务**发出。
* **F-SCRAPE-004 (数据抓取 - ScrapeParser):** 引擎 (`ScrapeParser.java`) 必须能抓取以下数据：
    * 价格 (Buybox价格)
    * BSR (主排名)
    * BSR Category (大类)
    * BSR Subcategory (子类)
    * BSR Subcategory Rank (子类排名)
    * 五点描述 (Bullet Points)
    * 评论总数
    * 平均星级
    * 主图URL (用于MD5 HASH对比)
    * A+ 内容区域的原始HTML (用于MD5 HASH对比)
* **F-SCRAPE-005 (V2.0 库存抓取):** 引擎必须能通过 `SeleniumScraper` **执行“999加购法”**（模拟加购到999件）来抓取**精准的预估库存**。静态解析（"Only 7 left"）仅作为 `HttpClient/Jsoup` 抓取时的补充手段。
* **F-SCRAPE-006 (V2.0 差评解析):** 引擎必须能解析评论区，抓取最新评论（至少前10条）的**Review ID, 评分(Rating), 评论日期(Date), 和评论内容(Text)**。
* **F-SCRAPE-007 (数据存储):** 每次抓取成功后，所有数据点必须连同**时间戳**一起存入**MySQL**数据库 (`asin_history` 表)。

### F-MOD-4: 警报与数据 (Alert & Data Engine)
* **F-DATA-001 (数据对比):** 在每次新数据（F-SCRAPE-007）存入后，`AlertService` 必须立即将其与**上一次**的抓取数据（从 `asinHistoryRepository` 获取）进行对比。
* **F-DATA-002 (警报触发器 - 价格):** `newPrice != oldPrice` -> 触发警报。
* **F-DATA-003 (警报触发器 - 库存):** `newInventory < inventoryThreshold` (F-ASIN-004中设置的阈值) -> 触发警报。
* **F-DATA-004 (V2.0 警报触发器 - 差评):** `AlertService` 必须对比抓取到的新评论列表 (来自F-SCRAPE-006) 和 `review_alert` 表中的已有记录（按Review ID去重）。如果发现**新的1-3星差评** -> 触发警报。
* **F-DATA-005 (警报触发器 - 主图):** `MD5(newImageUrl) != MD5(oldImageUrl)` -> 触发警报。
* **F-DATA-006 (警报触发器 - 标题/五点/A+):** `MD5(newText) != MD5(oldText)` -> 触发警报。
* **F-DATA-007 (警报记录与推送):**
    * **(P0) 数据库记录：** 所有被触发的警报，必须被格式化并**写入 `alert_log` 数据库表**（作为事实来源）。
    * **(P1) 钉钉推送 (可选)：** *如果* `dingtalk.webhook` 环境变量已配置，系统**必须**同时将警报格式化为 **纯文本消息** 并推送到钉钉。
    * **(V2.0) 钉钉差评内容：** 对于差评告警，**必须包含该差评的具体内容(Text)**。
* **F-DATA-008 (历史曲线):** ASIN详情页（F-ASIN-006）必须能调用API，获取该ASIN的**历史数据**（价格、BSR、库存），并在前端使用**图表库 (Chart.js)** 绘制成折线图。
* **F-DATA-009 (V2.0 差评存储):** 所有新发现的评论（无论星级，来自F-SCRAPE-006）都应被存入 `review_alert` 表中（F-DATA-004 仅使用其中的1-3星）。

### F-MOD-5: API 端点 (API Endpoints) - (供AI生成前端使用)
* **F-API-001 (OpenAPI):** 系统必须使用 `springdoc-openapi` 自动生成 `/v3/api-docs` (OpenAPI 3.0 JSON) 规范。
* **F-API-002 (Auth - V3.0):** (V3.0 优先级)
* **F-API-003 (ASIN Management):** (V1.0 开放)
    * `GET /api/asin`
    * `POST /api/asin`
    * `DELETE /api/asin/{id}`
    * `PUT /api/asin/{id}/config`
* **F-API-004 (Data Query):** (V1.0 开放)
    * `GET /api/asin/{id}/history?range=...` (例如 `?range=30d`)
* **F-API-005 (Alert Query):** (V1.0 开放)
    * `GET /api/alerts?limit=50&status=NEW` (获取全局**最新**警报日志列表)
    * `GET /api/asin/{id}/alerts` (获取特定ASIN的所有警报)
* **F-API-006 (V2.0 Review Query):** (V2.0 开放)
    * `GET /api/asin/{id}/reviews?rating=negative` (获取特定ASIN的已存储差评列表)

---

## 4. 非功能性需求 (Non-Functional Requirements, NFRs)

| 类别 | 需求 (NFR) |
| :--- | :--- |
| **性能** | **NFR-P-001:** API 响应时间 (非爬虫) 必须在 500ms 以下。 |
| | **NFR-P-002:** 仪表盘页面 (UI) 加载时间必须在 3 秒以下。 |
| | **NFR-P-003:** 单个ASIN的全量抓取（含Selenium）应在 90 秒内完成。 |
| **安全** | **NFR-S-001 (V3.0):** (V3.0) 所有Web流量必须使用 **HTTPS (SSL)**。 |
| | **NFR-S-002:** 所有敏感密钥（数据库密码、代理APIKey、**钉钉Webhook**）**绝不能**硬编码在代码中，必须使用环境变量或 `.properties` / `.yml` 文件注入。 |
| | **NFR-S-003 (V1.0/V2.0):** V1.0/V2.0 系统假定部署在私有网络，如需公网访问，**必须**由Nginx层配置IP白名单或Basic Auth。 |
| **可靠性** | **NFR-R-001:** 爬虫必须有**重试机制**（代码实现为：`@Retryable` 3次尝试，1小时 `backoff`）。 |
| | **NFR-R-002:** 系统应7x24小时运行，Docker容器必须配置为 `restart: always`。 |
| | **NFR-R-003:** 数据库数据必须**持久化**（使用Docker Volume）。 |
| | **NFR-R-004 (HTML转储):** 当抓取命中防爬或关键字段缺失时，系统必须将原始HTML转储到 `logs/html-dump` 目录。 |
| | **NFR-R-005 (转储清理):** 系统必须有定时任务 (`HtmlDumpCleaner`)，定期清理 `NFR-R-004` 中N天前的HTML文件。 |
| **配置** | **NFR-C-001:** 必须在 `application.yml` 或环境变量中提供**钉钉Webhook** (`dingtalk.webhook`) 的配置项。 |
| **部署** | **NFR-D-001:** 整个应用（后端、**MySQL**数据库、Nginx）必须通过 `docker-compose.yml` **一键编排**。 |
| | **NFR-D-002:** Nginx必须配置为反向代理：`your.domain.com/` 指向前端静态文件，`your.domain.com/api/` 指向Spring Boot后端。 |
| **技术栈** | **NFR-T-001:** 数据库统一使用 **MySQL 8**。 |
| | **NFR-T-002:** 后端技术栈为 **Java 21 + Spring Boot 3.x**。 |

---

## 5. V2.0 启动标准 (修订)

* V1.4 (代码校准版) 的所有功能稳定运行。
* **(V2.0) “999加购法”** (F-SCRAPE-005) 已实现，`asin_history` 表中的 `inventory` 字段能反映精准的高位库存。
* **(V2.0) 差评内容** (F-SCRAPE-006, F-DATA-009) 已抓取并存入 `review_alert` 表。
* **(V2.0) 钉钉差评告警** (F-DATA-007) **必须包含差评内容**。
* **(V2.0) 新API** (`GET /api/asin/{id}/reviews`) 已实现并返回 `review_alert` 表中的数据。
* 所有 **P0** 和 **P1** 级别的用户故事均已实现并通过测试。
* `springdoc-openapi` 文档已自动生成且内容准确。
* 已成功在**线上服务器**（美国）通过Docker Compose部署，并集成了**付费住宅代理**。

---

## 6. 附录：数据库表（简述）

| 表名 | 说明 | 关键字段 |
| --- | --- | --- |
| `asin` | 监控的 ASIN 主表 | asin, site, nickname, inventory_threshold |
| `asin_history` | 每次抓取的时间序列数据 | asin_id, price, bsr, inventory, image_md5, aplus_md5, created_at |
| `alert_log` | 触发的警报记录 | asin_id, type, message, created_at |
| `review_alert` | 抓取的评论（含差评） | asin_id, review_id, rating, content, review_date, created_at |

（实体字段以实际代码/迁移文件为准）

## 7. 版本变更日志 (Changelog)
| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V2.0 | 2025-11-01 | 引入 999 加购法与差评内容抓取；添加 Actuator；优化 OpenAPI。 |
| V1.4 | 2025-10 | 稳定化基础抓取与警报流程。 |

## 8. 未来路线 (Roadmap 概要)
| 版本 | 计划 | 关键特性 |
| --- | --- | --- |
| V2.x | 优化抓取性能 | 代理池与失败熔断、差评情感分析 |
| V3.0 | 安全与可视化升级 | 用户认证、角色管理、排名跟踪、指标监控 |
| V3.x | 智能增强 | 基于差评的自动回复建议、价格策略模拟 |

---
