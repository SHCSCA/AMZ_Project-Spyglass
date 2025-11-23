<!--
  自动为 AI 编码代理准备的仓库指导文件。
  目标：让自动代码代理（Copilot、Assistant 类工具）快速上手本项目，了解架构、关键文件、开发流程与约定。
-->

# Copilot / AI 代码代理说明（精简）

下面的说明基于仓库根目录的 `README.md`（项目规格）与约定结构。主要面向自动化编码代理，内容简洁、可执行。

## 主要架构（大局观）
- 后端：Spring Boot（API-first）。核心职责：爬虫调度、数据入库、告警触发与对外 REST API。
- 爬虫：静态抓取使用 Jsoup，动态/库存抓取使用 Selenium；所有外部请求需通过住宅代理（例如 Bright Data）。
- 存储：mysql 保存时序/实体数据（价格、BSR、评论、库存快照等）。
- 通知：通过钉钉机器人 Webhook 推送告警（暂时不实现）。
- 部署：Docker + docker-compose（One-click 启动后端与 MySQL）。

## 快速可用路径与命名约定
- 后端服务（预期）： `spyglass-backend/` 或 `backend/`，Java 源码在 `src/main/java`。
- 常见包分层：`controller`、`service`、`repository`、`model/entity`、`scraper`。
- OpenAPI 文档：`/v3/api-docs`（springdoc-openapi） — AI 可直接读取以生成或更新前端。
- 定时任务：查找 `@Scheduled` 注解的类（抓取调度）。

## 关键文件与信号（在修改或实现时优先查看/更新）
- `README.md`（项目规格） — 包含所有业务规则（警报触发、抓取频率、重要端点）。
- `docker-compose.yml` 或 `docker/` — 启动环境与数据库卷配置，注意 `POSTGRES_*` 环境变量名。
- `application.yml` / `application.properties` — 数据源、代理配置、JWT 密钥以环境变量注入为准。
- `src/**/scraper/**` — 查找 Jsoup / Selenium 实现及代理注入点。
- `src/**/service/**` — 寻找比较/告警逻辑（MD5 比较主图/A+、差评去重、价格/库存阈值判断）。

## 开发/构建/运行（可执行命令）
（基于常见 Spring Boot + Maven 约定）

本地构建并运行（如果存在 Maven Wrapper）：

```bash
cd <repo-root-or-backend>
./mvnw -DskipTests package
./mvnw spring-boot:run
```

使用 Docker Compose 启动（推荐进行集成测试）：

```bash
docker compose up --build
```

注意：所有敏感配置（DB 密码、JWT 密钥、代理 Key、钉钉 Webhook）应来自环境变量或 `.env`，不得硬编码。

## 项目特有约定（从 README 可观察到的实现细节）
- API-first：后端必须公开 OpenAPI（用于 AI 生成前端），优先保证 `/v3/api-docs` 的完整性。
- 抓取策略：每次抓取需持久化时间戳并与上次抓取数据差异化比较（价格、库存、评论、主图/A+的 MD5）。
- 代理强制：所有对亚马逊的请求应走可替换的代理层（实现处应显式调用代理客户端或注入代理配置）。
- 异步与容错：抓取任务应异步执行并具备重试策略（如失败后延迟重试），避免单点阻塞。

## 给 AI / Copilot 的具体编码提示（示例）
- 当实现抓取比较逻辑时，遵循 README 中的触发规则，例如：
  - 价格变更 -> `newPrice != oldPrice` -> 生成 PriceAlert 并入库/推送。
  - 差评 -> 新的 1-3 星评论（以评论唯一 id 或内容+日期去重）触发 NegativeReviewAlert。
  - 主图/A+ -> 比较 HTML 或 URL 的 MD5 值变化。
- 在添加新 API 时，务必让 `springdoc-openapi` 扫描到并生成 `/v3/api-docs`。
- 在实现 Selenium 抓取库存时，封装一层可替换的 DriverProvider（便于在 CI/容器中切换 headless/remote）。

## 变更/提交规则（针对自动化代理）
- 小而明确的 PR：每次改动只实现单一职责（例如：新增一个抓取器、或只修改告警触发规则）。
- 测试优先：在修改核心数据流程（抓取 -> 比较 -> 告警）时同时添加单元测试或集成测试（可用 Testcontainers + PostgreSQL）。

## 如果找不到源码位置（AI 代理处理流程）
1. 检查根目录是否存在 `spyglass-backend/` 或 `backend/`；若不存在，优先创建后端骨架并将 `README.md` 中的规范作为实现契约。
2. 生成 OpenAPI stub（Controller + DTO）以供前端与自动化代理并行工作。

## 本仓库已生成的骨架
- 已在仓库内生成了一个最小后端骨架：`spyglass-backend/`（含 `pom.xml`、主类、示例 Controller、ScraperService、`application.yml` 与 Thymeleaf 模板）。
  
该骨架用于快速验证构建与本地运行，AI 代理在实现真实逻辑时应替换或扩展这些文件。

---

如果你希望，我可以把 README 中的 API 规范直接转成 OpenAPI stub（Controller + DTO）或生成一个最小的后端骨架；告诉我你想要的优先项（例如：登录 + ASIN 列表 + 抓取调度）。
