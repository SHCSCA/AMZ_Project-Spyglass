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
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/v3/api-docs | head -n 20
```
启动后：
* 后端：`http://localhost:8080`
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
# 添加 ASIN
curl -X POST http://localhost:8080/api/asin \
	-H 'Content-Type: application/json' \
	-d '{"asin":"B0TEST1234","site":"US","nickname":"Test Product","inventoryThreshold":20}'

# 查询列表
curl 'http://localhost:8080/api/asin?page=0&size=20'

# 查询历史（最近30天，分页第0页）
curl 'http://localhost:8080/api/asin/1/history?range=30d&page=0&size=100'
```

### 示例：警报与差评（分页与过滤）
```
# 获取最新警报
curl 'http://localhost:8080/api/alerts?page=0&size=50&type=PRICE_CHANGE'

# 获取某 ASIN 的警报
curl http://localhost:8080/api/asin/1/alerts

# 获取某 ASIN 的差评（仅 1-3 星）
curl 'http://localhost:8080/api/asin/1/reviews?rating=negative&page=0&size=50'

### 分页与查询参数说明
| 端点 | 参数 | 说明 |
| --- | --- | --- |
| `GET /api/asin` | `page` / `size` | ASIN 列表分页，按 id DESC 排序 |
| `GET /api/asin/{id}/history` | `range` / `page` / `size` | 时间范围与分页（历史数据内部按 snapshotAt DESC）|
| `GET /api/alerts` | `page` / `size` / `type` | 最新告警分页，可按类型过滤 |
| `GET /api/asin/{id}/reviews` | `rating=negative` / `page` / `size` | 负面评论过滤（1-3 星）与分页 |

### 使用外部数据库的注意事项
* 需要确保应用容器能够访问外部 MySQL（安全组 / 防火墙开放 3306）。
* 建议为生产环境开启只读账号与最小权限策略。
* 如果使用云数据库（RDS 等），启用自动备份与多可用区。 

### 性能调优建议（可选）
| 场景 | 建议 |
| --- | --- |
| 历史数据量大 | 为 `asin_history(asin_id, snapshot_at)` 建复合索引 |
| 告警查询频繁 | 为 `alert_log(alert_type, alert_at)` 建索引 |
| 差评查询频繁 | 为 `review_alert(asin_id, rating)` 建索引 |
| 高并发 | 引入 Caffeine/Redis 缓存热点 ASIN 基础信息 |
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

> 若你需要自动化补齐尚未实现的 Alert / Review API，请在任务中指出，我可以继续生成控制器、DTO 与基础测试。