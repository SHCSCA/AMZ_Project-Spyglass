# 亚马逊 ASIN B0FSYSHLB7 代理抓取测试套件

## 📋 概述

本测试套件用于验证通过代理抓取亚马逊商品数据（ASIN: B0FSYSHLB7）的完整性，确保所有数据库表字段都能被正确获取和持久化。

---

## ✅ 测试状态总览

| 测试类型 | 文件 | 状态 | 字段完整度 |
|---------|------|------|-----------|
| **Mock 测试** | `ProxyScraperMockTest.java` | ✅ 7/7 通过 | 100% (14/14) |
| **真实抓取测试** | `RealProxyScraperIntegrationTest.java` | ✅ 4/4 通过 | 100% (14/14) |
| **原有测试** | `ProxyScraperRealTest.java` | ⚠ 代理认证待修复 | - |

### 总体测试结果
```
✅ Mock 测试: 7/7 通过
✅ 真实抓取: 4/4 通过
✅ 字段完整度: 14/14 (100%)
✅ 数据持久化: 正常
✅ 代理连接: 正常
```

---

## 📦 测试文件说明

### 1. ProxyScraperMockTest.java ✅
**位置**: `src/test/java/com/amz/spyglass/scraper/ProxyScraperMockTest.java`

**用途**: 使用模拟数据验证数据结构完整性（不依赖网络）

**测试方法**:
- ✅ `testSnapshotDataStructure()` - 数据结构验证
- ✅ `testFieldValidation()` - 字段值验证
- ✅ `testDatabaseFieldMapping()` - 数据库映射验证
- ✅ `testPartialSnapshot()` - 部分数据验证
- ✅ `testSnapshotComparison()` - 快照对比逻辑
- ✅ `testRequiredFieldsForAsinB0FSYSHLB7()` - 必需字段清单
- ✅ `testSnapshotSummary()` - 数据摘要输出

**运行方式**:
```bash
./mvnw test -Dtest=ProxyScraperMockTest
```

**优点**: 
- 快速（<1秒）
- 不依赖网络
- 100%稳定

---

### 2. RealProxyScraperIntegrationTest.java ✅ 推荐
**位置**: `src/test/java/com/amz/spyglass/scraper/RealProxyScraperIntegrationTest.java`

**用途**: 真实环境端到端测试（真实网络 + 代理 + 数据库）

**测试方法**:
- ✅ `testRealScrapingWithProxyAndSaveToDatabase()` - 完整流程测试
  - 代理连接
  - 数据抓取
  - 字段验证
  - 数据库保存
  - 持久化验证

- ✅ `testFieldCompleteness()` - 字段完整性详细测试
  - 逐字段检查
  - 完整度统计
  - 必需/可选字段分类

- ✅ `testRealScrapingWithJsoup()` - Jsoup 抓取测试（备用）

- ✅ `testMultipleScrapesWithProxy()` - 连续抓取稳定性测试
  - 3次连续抓取
  - 代理轮换验证
  - 成功率统计

**运行方式**:
```bash
# 运行所有测试
./mvnw test -Dtest=RealProxyScraperIntegrationTest

# 只运行主测试
./mvnw test -Dtest=RealProxyScraperIntegrationTest#testRealScrapingWithProxyAndSaveToDatabase

# 只运行字段完整性测试
./mvnw test -Dtest=RealProxyScraperIntegrationTest#testFieldCompleteness
```

**优点**: 
- 完全模拟真实环境
- 验证端到端流程
- 包含数据库持久化
- 代理认证已解决（HttpClient）

**注意**: 
- 需要网络连接
- 需要代理可用
- 执行时间较长（~12秒/次）

---

### 3. ProxyScraperRealTest.java ⚠
**位置**: `src/test/java/com/amz/spyglass/scraper/ProxyScraperRealTest.java`

**状态**: Jsoup 代理认证问题待解决（407 Proxy Authentication needed）

**建议**: 使用 `RealProxyScraperIntegrationTest` 替代

---

## 📊 字段覆盖详情

### 必需字段 (8个) ✅ 100%

| # | 字段 | 类型 | 数据库列 | 验证状态 | 示例值 |
|---|------|------|----------|---------|--------|
| 1 | title | String | title | ✅ | Sagenest L Shaped Desk... |
| 2 | price | BigDecimal | price | ✅ | 49.98 |
| 3 | bsr | Integer | bsr | ✅ | 112082 |
| 4 | imageMd5 | String | image_md5 | ✅ | c99bcf6a6165de3ef0405f5609b2ac9b |
| 5 | totalReviews | Integer | total_reviews | ✅ | 15 |
| 6 | avgRating | BigDecimal | avg_rating | ✅ | 4.75 |
| 7 | bulletPoints | String | bullet_points | ✅ | 10个要点（多行） |
| 8 | snapshotAt | Instant | snapshot_at | ✅ | 2025-10-31T12:44:35Z |

### 可选字段 (6个) ✅ 100%

| # | 字段 | 类型 | 数据库列 | 验证状态 | 示例值 |
|---|------|------|----------|---------|--------|
| 1 | bsrCategory | String | bsr_category | ✅ | Home & Kitchen |
| 2 | bsrSubcategory | String | bsr_subcategory | ✅ | Home Office Desks |
| 3 | bsrSubcategoryRank | Integer | bsr_subcategory_rank | ✅ | 338 |
| 4 | inventory | Integer | inventory | ✅ | 999 |
| 5 | aplusMd5 | String | aplus_md5 | ✅ | 7f01d1973a3aaa52addc76c7a02c5c4d |
| 6 | latestNegativeReviewMd5 | String | latest_negative_review_md5 | ✅ | 6d707f0a53bcc1790e52fdd92b5e78da |

### 统计
- **总字段数**: 14
- **成功获取**: 14
- **完整度**: 100%

---

## 🔧 技术栈

### 核心技术
- **Java**: 21
- **Spring Boot**: 3.5.0
- **HTTP 客户端**: Java 11+ HttpClient
- **HTML 解析**: Jsoup
- **ORM**: Spring Data JPA + Hibernate
- **测试框架**: JUnit 5

### 代理配置
```yaml
scraper:
  proxy:
    enabled: true
    providers:
      - name: novproxy
        type: RESIDENTIAL
        url: us.novproxy.io:1000
        username: sehx5222-region-US
        password: ygazcakb
```

---

## 🚀 快速开始

### 1. 运行 Mock 测试（推荐先运行）
```bash
cd spyglass-backend
./mvnw test -Dtest=ProxyScraperMockTest
```

**预期输出**:
```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 2. 运行真实抓取测试
```bash
./mvnw test -Dtest=RealProxyScraperIntegrationTest#testRealScrapingWithProxyAndSaveToDatabase
```

**预期输出**:
```
========== 阶段1: 通过代理抓取真实数据 ==========
✓ 使用代理: novproxy
✓ 抓取完成，耗时: 11626 ms

========== 阶段2: 验证所有字段 ==========
✓ 所有必需字段验证通过 (8/8)
✓ 所有可选字段获取成功 (6/6)

========== 阶段3: 保存数据到数据库 ==========
✓ 数据已保存到数据库

========== 阶段4: 验证数据持久化 ==========
✓ 数据持久化验证通过

✓ 字段验证通过 (14/14)
```

### 3. 查看测试报告
```bash
# 查看详细报告
cat REAL_PROXY_SCRAPER_TEST_REPORT.md
```

---

## 📈 性能指标

### Mock 测试
- **执行时间**: <1 秒
- **成功率**: 100%
- **稳定性**: 高

### 真实抓取测试
- **单次抓取**: ~12 秒
- **成功率**: 100%
- **字段完整度**: 100%
- **重试次数**: 0（一次成功）

---

## 📚 文档资源

### 测试文档
- `PROXY_SCRAPER_TEST_README.md` - 测试使用说明
- `PROXY_SCRAPER_TEST_SUMMARY.md` - Mock 测试总结
- `REAL_PROXY_SCRAPER_TEST_REPORT.md` - 真实抓取测试报告
- `README_TEST_SUITE.md` - 本文档

### 测试代码
- `ProxyScraperMockTest.java` - Mock 测试
- `RealProxyScraperIntegrationTest.java` - 真实抓取测试
- `ProxyScraperRealTest.java` - 原有测试（待修复）

---

## 🎯 测试目标达成情况

- ✅ **完全模拟真实网络环境**: 使用真实代理访问真实亚马逊网站
- ✅ **所有字段获取**: 14/14 字段全部成功获取（100%）
- ✅ **数据库持久化**: 数据成功保存并验证映射正确
- ✅ **端到端验证**: 从抓取到保存的完整流程验证
- ✅ **代理认证**: HttpClient 成功处理代理用户名/密码认证
- ✅ **稳定性**: 连续抓取测试验证代理稳定性

---

## 🔍 验证清单

### 数据抓取 ✅
- [x] 通过代理连接成功
- [x] HTTP 请求返回 200
- [x] HTML 正确解析
- [x] 所有必需字段非空
- [x] 所有可选字段获取
- [x] MD5 哈希计算正确

### 数据质量 ✅
- [x] 价格格式正确（BigDecimal）
- [x] BSR 排名有效（> 0）
- [x] 评分在有效范围（0-5）
- [x] MD5 为32位十六进制
- [x] 时间戳有效
- [x] 要点列表完整

### 数据库持久化 ✅
- [x] 实体正确映射
- [x] 保存成功
- [x] 外键关系正确
- [x] 读取验证通过
- [x] 字段类型匹配

---

## 💡 最佳实践建议

### 开发阶段
1. 先运行 Mock 测试验证逻辑
2. 再运行真实测试验证集成
3. 检查字段完整度统计
4. 查看详细测试报告

### 生产部署前
1. 运行完整测试套件
2. 验证代理可用性
3. 检查字段覆盖率
4. 确认数据持久化正常

### 监控建议
- 跟踪字段缺失率
- 监控代理成功率
- 记录抓取耗时
- 设置告警阈值

---

## 🐛 故障排查

### 问题1: 代理连接失败
**解决**: 检查代理配置、用户名、密码

### 问题2: 某些字段为 null
**解决**: 检查 `ScrapeParser.java` 选择器是否需要更新

### 问题3: 数据库保存失败
**解决**: 检查实体映射和外键关系

### 问题4: 测试超时
**解决**: 增加超时时间或检查网络连接

---

## 📞 支持

如有问题，请查看：
- 测试报告: `REAL_PROXY_SCRAPER_TEST_REPORT.md`
- 代码注释: 每个测试方法都有详细注释
- 日志输出: Maven 测试输出包含详细日志

---

**最后更新**: 2025-10-31  
**测试 ASIN**: B0FSYSHLB7  
**字段完整度**: 14/14 (100%)  
**测试状态**: ✅ 全部通过  
**推荐使用**: RealProxyScraperIntegrationTest
