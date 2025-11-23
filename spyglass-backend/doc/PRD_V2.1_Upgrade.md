# 产品需求文档 (PRD): 亚马逊竞品情报系统 V2.1 (稳定性与深度洞察版)

| 文档版本 | V2.1 |
| :--- | :--- |
| **项目名称** | 亚马逊竞品情报系统 (Project Spyglass) |
| **状态** | 待开发 |
| **优先级** | P0 (稳定性) > P1 (数据精度) > P2 (业务洞察) |
| **最后修订** | 2025年11月23日 |
| **修订人** | 产品经理 |

---

## 1. 背景与目标
系统 V2.0 已具备基础的抓取与告警能力。根据运行反馈，数据库字段截断问题已通过手动调整 Schema 解决，但在高并发下的代理稳定性、Chrome 资源消耗以及数据深度（真实库存、利润分析）方面仍需升级。

**本版本核心目标：**
1.  **夯实地基：** 彻底解决爬虫 407 错误与 Selenium 崩溃问题，确保 7x24 小时稳定运行。
2.  **数据精准化：** 实现“999加购法”以获取真实库存数据，突破页面 "In Stock" 的模糊限制。
3.  **决策智能化：** 引入关键词排名监控与利润计算，从“看数据”升级为“算利润”。

---

## 2. 核心技术约束：数据库与架构 (Critical)

### 2.1 严禁自动 Schema 更新 (Manual Schema Policy)
鉴于生产环境数据库表结构已由人工手动维护（如将 `alert_log` 调整为 `LONGTEXT`），代码层必须严格遵循以下规范以防止覆盖配置：

* **配置锁定确认：** `src/main/resources/application.yml` 中以下配置必须保持不变：
    ```yaml
    spring:
      jpa:
        hibernate:
          ddl-auto: none  # 严禁改为 update/create
      flyway:
        enabled: false    # 禁用自动迁移
    ```
* **禁止自动 DDL：** 后端代码中**严禁**包含任何自动执行 `ALTER TABLE`、`CREATE TABLE` 或 `DROP TABLE` 的逻辑。
* **手动变更流程：** 所有数据库变更（如新增表、扩展字段）必须使用本文档附录中的 SQL 脚本，由运维人员手动执行。

---

## 3. 功能需求详解

### 3.1 稳定性专项优化 (Priority P0 - Stability)

#### F-STABLE-001: 爬虫代理重构
* **问题：** 日志显示大量 `Unable to tunnel through proxy` (407)。Jsoup 使用全局 `Authenticator` 在多线程下不安全且易冲突。
* **需求：**
    * 废弃 `Authenticator.setDefault` 全局设置。
    * 在 `JsoupScraper` 和 `HttpClientScraper` 中，必须为**每个请求**独立构建并注入 `Proxy-Authorization: Basic <Base64>` Header。
    * 实现简单的熔断机制：若同一代理 IP 连续失败 5 次，暂时将其移出轮询队列 10 分钟。

#### F-STABLE-002: Selenium 资源隔离与按需加载
* **问题：** Docker 内 Chrome 频繁崩溃 (`session not created`)，且占用大量内存。
* **需求：**
    * **架构解耦：** 移除应用容器内的本地 Chrome 依赖。改用独立的远程 WebDriver 服务（推荐使用 `selenium/standalone-chrome` 容器）。
    * **按需调用：** 代码逻辑中，仅当 `HttpClient` 抓取失败或需要执行“加购法”时才初始化 WebDriver，任务完成后立即执行 `driver.quit()` 释放资源。
    * **并发限制：** 引入 `Semaphore` 限制同时运行的浏览器实例不超过 2 个（防止内存溢出）。

#### F-STABLE-003: JPA 关联查询优化
* **问题：** ASIN 列表接口偶发 `LazyInitializationException`。
* **需求：**
    * 在 `AsinRepository` 的查询方法上使用 `@EntityGraph(attributePaths = {"group"})`，确保在一次 SQL 查询中连带抓取分组信息，避免 Session 关闭后的懒加载异常。

---

### 3.2 数据精度升级 (Priority P1 - Accuracy)

#### F-DATA-001: 真实库存侦察 ("999加购法")
* **场景：** 页面仅显示 "In Stock"，无法判断具体数量，误导补货与竞争策略。
* **逻辑（Selenium）：**
    1.  打开商品页，定位并点击 "Add to Cart"（加入购物车）。
    2.  跳转至购物车页面 (`/gp/cart/view.html`)。
    3.  修改购买数量输入框为 `999` 并点击更新。
    4.  **抓取点：** 捕获弹出的错误提示文本（例如："This seller has only 483 of these available"）。
    5.  解析数字 `483` 并存入 `asin_history.inventory`。
* **异常处理：** 若无报错直接允许购买 999，则标记库存为 `999+`（或数据库存 `1000` 表示充足）。

#### F-DATA-002: 促销活动监控 (Coupons & Deals)
* **场景：** 竞品价格未变但销量激增，通常是因为开启了 Coupon 或秒杀。
* **需求：**
    * **Coupon 抓取：** 检测价格下方的绿色 Coupon 徽章，提取优惠金额（如 "$10 off" 或 "5%"）。
    * **Deal 抓取：** 检测 "Lightning Deal" 或 "Limited time deal" 标识。
    * **数据落地：** 在 `asin_history` 表新增 `coupon_value` 和 `is_lightning_deal` 字段（见附录 SQL）。

---

### 3.3 业务深度洞察 (Priority P2 - Insight)

#### F-BIZ-001: 关键词排名监控 (Keyword Ranking)
* **需求：**
    * 允许为每个 ASIN 配置 3-5 个核心关键词（新增表 `asin_keywords`）。
    * 每日一次（独立于 ASIN 详情抓取），模拟搜索该关键词。
    * **抓取点：** 遍历搜索结果前 3 页，匹配 ASIN，记录其自然排名位置（Organic Rank）和广告排名位置（Sponsored Rank）。
    * **告警：** 当核心词自然排名跌出第一页（>48名）时触发告警。

#### F-BIZ-002: 利润反推计算器 (Profitability Calc)
* **需求：**
    * 在 ASIN 详情页增加“成本配置”区域（新增表 `asin_costs`）：输入`FOB采购成本`、`头程运费`。
    * 集成 FBA 费率估算逻辑（基于尺寸重量，粗略估算）。
    * **实时计算：** `预估毛利 = 当前售价 - 亚马逊佣金(15%) - FBA配送费 - 头程 - FOB`。
    * **展示：** 在价格趋势图下方，同步展示“预估毛利”趋势曲线。

#### F-BIZ-003: 评论情感 AI 摘要 (Review Sentiment)
* **需求：**
    * 对接 OpenAI/DeepSeek API（需在环境变量配置 Key）。
    * **触发时机：** 每次抓取到新差评（1-3星）时。
    * **逻辑：** 将差评内容发送给 AI，提示词：“请用一句话总结用户的核心抱怨点”。
    * **输出：** 将 AI 总结存入数据库，并在钉钉告警中展示总结内容（如：“用户主要抱怨螺丝孔位不匹配”），替代冗长的原文。

---

## 4. 非功能性需求 (NFR)

* **部署架构：** 必须支持 Docker Compose 一键部署，且数据库数据卷持久化。
* **日志规范：** 生产环境日志级别为 `INFO`，但在出现抓取错误时必须记录完整的异常堆栈和 `cid` (Correlation ID) 以便追踪。
* **安全性：** 所有敏感配置（API Key、DB 密码、代理账号）必须通过环境变量注入，不得硬编码。

---

## 5. 附录：数据库变更脚本 (SQL)

**重要提示：** 请运维人员手动在数据库执行以下 SQL，以支持 V2.1 新功能。

### 5.1 修正现有表 (Schema Correction)
*说明：当前的 `review_alert.review_text` 是 `tinytext` (最大255字符)，对于存储完整的差评内容严重不足，必须扩容。*

```sql
-- 扩容评论内容字段，防止截断
ALTER TABLE `review_alert` 
    MODIFY COLUMN `review_text` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '评论正文内容(支持长文本)';
```

### 5.1 扩展现有表 (Extend Existing Tables)
*说明：为 asin_history 增加促销信息字段。*

```sql
ALTER TABLE `asin_history`
    ADD COLUMN `coupon_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '优惠券面额(例如 $10 off 或 5%)',
    ADD COLUMN `is_lightning_deal` tinyint(1) NULL DEFAULT 0 COMMENT '是否正在进行秒杀活动(1=是, 0=否)';
```

### 5.3新增功能表 (New Tables)
*说明：支持关键词监控与成本计算的新表，包含完整的中文注释。*
```sql
-- ============================================================
-- 表名：asin_keywords
-- 说明：ASIN关键词监控配置表，用于记录需要监控排名的核心关键词
-- ============================================================
CREATE TABLE `asin_keywords` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联ASIN表主键ID',
  `keyword` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需监控的核心关键词',
  `last_organic_rank` int NULL DEFAULT NULL COMMENT '最新自然排名(空值代表未找到)',
  `last_sponsored_rank` int NULL DEFAULT NULL COMMENT '最新广告排名(空值代表未找到)',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_asin_keywords_asin_id`(`asin_id` ASC) USING BTREE,
  CONSTRAINT `fk_asin_keywords_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'ASIN关键词监控配置表' ROW_FORMAT = Dynamic;

-- ============================================================
-- 表名：asin_costs
-- 说明：ASIN成本与利润计算配置表，用于存储FOB和头程等成本信息
-- ============================================================
CREATE TABLE `asin_costs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asin_id` bigint NOT NULL COMMENT '关联ASIN表主键ID',
  `fob_cost` decimal(10, 2) NULL DEFAULT 0.00 COMMENT 'FOB采购成本(美元)',
  `shipping_cost` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '头程运费(美元)',
  `fba_fee_override` decimal(10, 2) NULL DEFAULT NULL COMMENT 'FBA配送费(可选，若不填则自动估算)',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `ux_asin_costs_asin_id`(`asin_id` ASC) USING BTREE COMMENT '每个ASIN只能有一条成本配置',
  CONSTRAINT `fk_asin_costs_asin` FOREIGN KEY (`asin_id`) REFERENCES `asin` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'ASIN成本与利润计算配置表' ROW_FORMAT = Dynamic;
```