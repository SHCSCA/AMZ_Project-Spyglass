package com.amz.spyglass.scraper;

import java.math.BigDecimal;
import java.net.MalformedURLException; // 添加日志注解导入
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.amz.spyglass.config.ScraperProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Selenium 抓取器（用于处理 JS 渲染的页面，如库存 / 动态加载价格 / 评价等）。
 * 说明：用于补充 Jsoup 静态抓取无法获取或为空的字段。
 */
@Slf4j
@Service
@Qualifier("seleniumScraper")
public class SeleniumScraper implements Scraper {

    // 移除不存在的 WebDriverManager 字段
    private final ProxyManager proxyManager;
    private final ScrapeParser scrapeParser;
    private final ScraperProperties scraperProperties;
    private final Semaphore driverSemaphore;
    private final URL remoteWebDriverUrl;
    private final long acquireTimeoutSeconds;

    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})|\\d+\\.\\d{2}|\\d+)");
    // 购物车页面中数量输入框的选择器
    private static final By CART_QUANTITY_INPUT_SELECTOR = By.cssSelector("input[name='quantity']");
    // 购物车中商品行的选择器，用于定位特定ASIN
    private static final By CART_ITEM_SELECTOR = By.cssSelector("div[data-asin]");
    // 商家限购提示的选择器
    private static final By PURCHASE_LIMIT_ERROR_SELECTOR = By.cssSelector(".sc-quantity-update-message .a-alert-content");


    public SeleniumScraper(ProxyManager proxyManager, ScrapeParser scrapeParser, ScraperProperties scraperProperties) {
        this.proxyManager = proxyManager;
        this.scrapeParser = scrapeParser;
        this.scraperProperties = scraperProperties;
        this.driverSemaphore = new Semaphore(Math.max(1, scraperProperties.getSeleniumMaxSessions()), true);
        try {
            this.remoteWebDriverUrl = URI.create(scraperProperties.getSeleniumRemoteUrl()).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException("无效的 Selenium 远程地址: " + scraperProperties.getSeleniumRemoteUrl(), e);
        }
        this.acquireTimeoutSeconds = Math.max(1L, scraperProperties.getSeleniumAcquireTimeoutSeconds());
    }

    /**
     * 使用Selenium执行“999加购法”获取精准库存。
     *
     * @param driver WebDriver实例
     * @param asin   目标ASIN
     * @return Optional<Integer> 库存数量。如果遇到商家限购，则返回-1。如果操作失败或未找到元素，则返回empty。
     */
    public Optional<Integer> fetchInventoryBy999Method(WebDriver driver, String asin) {
        try {
            // 1. 导航至购物车页面
            driver.get("https://www.amazon.com/gp/cart/view.html");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            // 2. 定位到目标ASIN所在的购物车条目
            WebElement cartItem = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(String.format("div[data-asin='%s']", asin))
            ));
            if (cartItem == null) {
                log.warn("ASIN {} not found in cart.", asin);
                return Optional.empty();
            }

            // 3. 找到数量输入框并尝试修改为999
            WebElement quantityInput = cartItem.findElement(CART_QUANTITY_INPUT_SELECTOR);
            quantityInput.clear();
            quantityInput.sendKeys("999");
            // 模拟回车或点击更新按钮来提交变更
            quantityInput.submit();

            // 4. 等待页面响应（关键步骤）
            // 等待一个明确的信号，比如一个加载动画消失，或者简单等待几秒
            try {
                Thread.sleep(3000); // 简单等待，生产环境建议换成更可靠的显式等待
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Inventory fetch sleep interrupted", e);
            }

            // 5. 检查是否存在“商家限购”的错误提示
            List<WebElement> limitErrors = cartItem.findElements(PURCHASE_LIMIT_ERROR_SELECTOR);
            if (!limitErrors.isEmpty() && limitErrors.get(0).isDisplayed()) {
                String errorMessage = limitErrors.get(0).getText();
                log.warn("Seller purchase limit detected for ASIN {}. Message: '{}'. Returning -1.", asin, errorMessage);
                // 返回-1作为特殊标记，表示遇到限购
                return Optional.of(-1);
            }

            // 6. 如果没有限购，读取最终的数量值
            String finalQuantity = cartItem.findElement(CART_QUANTITY_INPUT_SELECTOR).getAttribute("value");
            log.info("Successfully fetched inventory for ASIN {}: {}", asin, finalQuantity);
            return Optional.of(Integer.parseInt(finalQuantity));

        } catch (TimeoutException e) {
            log.error("Timeout waiting for cart item for ASIN {}. It might not be in the cart.", asin, e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch inventory for ASIN {} using Selenium. Error: {}", asin, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private ChromeOptions baseOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        return options;
    }

    private ProxyInstance attachProxy(ChromeOptions options) {
        Optional<ProxyInstance> borrowed = proxyManager.borrow();
        borrowed.ifPresent(proxy -> {
            options.addArguments("--proxy-server=" + proxy.getHost() + ':' + proxy.getPort());
            log.debug("[Selenium] 使用代理 {}", proxy);
        });
        if (borrowed.isEmpty()) {
            log.debug("[Selenium] 未配置可用代理，回退直连模式");
        }
        return borrowed.orElse(null);
    }

    private BorrowedDriver createDriver() {
        boolean acquired = false;
        try {
            acquired = driverSemaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("获取 Selenium 实例超时 (" + acquireTimeoutSeconds + "s)");
            }

            ChromeOptions options = baseOptions();
            ProxyInstance proxy = attachProxy(options);
            RemoteWebDriver driver = new RemoteWebDriver(remoteWebDriverUrl, options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
            return new BorrowedDriver(driver, proxy);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Selenium 实例被中断", ie);
        } catch (RuntimeException ex) {
            if (acquired) {
                driverSemaphore.release();
            }
            throw ex;
        }
    }

    private final class BorrowedDriver implements AutoCloseable {
        private final RemoteWebDriver driver;
        private final ProxyInstance proxy;
        private boolean closed;

        private BorrowedDriver(RemoteWebDriver driver, ProxyInstance proxy) {
            this.driver = driver;
            this.proxy = proxy;
        }

        private RemoteWebDriver driver() {
            return driver;
        }

        private ProxyInstance proxy() {
            return proxy;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                driver.quit();
            } catch (Exception ignored) {
                // best effort
            } finally {
                driverSemaphore.release();
            }
        }
    }

    @Override
    public String fetchTitle(String url) { // 去掉 throws Exception
        BorrowedDriver borrowed = createDriver();
        WebDriver driver = borrowed.driver();
        ProxyInstance proxy = borrowed.proxy();
        boolean success = false;
        try {
            driver.get(url);
            success = true;
            return driver.getTitle();
        } finally {
            if (proxy != null) {
                if (success) {
                    proxyManager.recordSuccess(proxy);
                } else {
                    proxyManager.recordFailure(proxy);
                }
            }
            borrowed.close();
        }
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) { // 去掉 throws Exception
        BorrowedDriver borrowed = createDriver();
        WebDriver driver = borrowed.driver();
        ProxyInstance proxy = borrowed.proxy();
        AsinSnapshotDTO dto = new AsinSnapshotDTO();
        boolean success = false;
        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            try { wait.until(d -> ((JavascriptExecutor)d).executeScript("return document.readyState").equals("complete")); } catch (Exception ignored) {}
            dto.setTitle(driver.getTitle());

            // 价格
            if (dto.getPrice() == null) {
                try {
                    WebElement priceEl = firstPresent(driver,
                            By.cssSelector("#corePriceDisplay_desktop_feature_div span.a-price span.a-offscreen"),
                            By.cssSelector("#corePrice_feature_div span.a-price span.a-offscreen"),
                            By.cssSelector("#corePrice_desktop span.a-price span.a-offscreen"),
                            By.cssSelector("span.a-price[data-a-color=price] span.a-offscreen"),
                            By.cssSelector("#priceblock_ourprice"),
                            By.cssSelector("#priceblock_dealprice"),
                            By.cssSelector("#tp_price_block_total_price_ww"));
                    if (priceEl != null) dto.setPrice(parsePrice(priceEl.getText()));
                } catch (Exception ignored) {}
            }

            // BSR
            if (dto.getBsr() == null) {
                try {
                    List<WebElement> detailBullets = driver.findElements(By.cssSelector("#detailBullets_feature_div li, #productDetails_detailBullets_sections1 tr"));
                    for (WebElement el : detailBullets) {
                        String txt = el.getText().toLowerCase(Locale.ROOT);
                        if (txt.contains("best sellers rank") || txt.contains("best sellers")) {
                            String digits = txt.replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) { dto.setBsr(Integer.valueOf(digits)); break; }
                        }
                    }
                    if (dto.getBsr() == null) {
                        try {
                            WebElement sr = driver.findElement(By.cssSelector("#SalesRank"));
                            String digits = sr.getText().replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) dto.setBsr(Integer.valueOf(digits));
                        } catch (NoSuchElementException ignoredInner) {
                            log.debug("Selenium 未找到 #SalesRank 元素: {}", ignoredInner.getMessage());
                        }
                    }
                } catch (WebDriverException e) {
                    log.debug("Selenium 解析 BSR 失败: {}", e.getMessage());
                }
            }

            // 五点要点
            if (dto.getBulletPoints() == null) {
                try {
                    List<WebElement> bullets = driver.findElements(By.cssSelector("#feature-bullets li"));
                    StringBuilder sb = new StringBuilder();
                    for (WebElement b : bullets) {
                        String t = b.getText().trim();
                        if (t.isEmpty()) {
                            continue;
                        }
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(t);
                    }
                    if (sb.length() > 0) {
                        dto.setBulletPoints(sb.toString());
                    }
                } catch (WebDriverException e) {
                    log.debug("Selenium 解析要点失败: {}", e.getMessage());
                }
            }

            // 主图 MD5 （基于 URL）
            if (dto.getImageMd5() == null) {
                try {
                    WebElement imageEl = firstPresent(driver,
                            By.cssSelector("#landingImage"), By.cssSelector("#imgTagWrapperId img"), By.cssSelector("img#main-image"));
                    if (imageEl != null) {
                        String src = imageEl.getDomAttribute("src");
                        if (src != null && !src.isEmpty()) dto.setImageMd5(md5Hex(src));
                    }
                } catch (RuntimeException ex) { log.debug("主图解析失败: {}", ex.getMessage()); }
            }

            // A+ 内容 MD5
            if (dto.getAplusMd5() == null) {
                try {
                    WebElement aplus = firstPresent(driver, By.cssSelector("#aplus"), By.cssSelector(".aplus"), By.cssSelector(".a-plus"));
                    if (aplus != null) {
                        String html = aplus.getDomProperty("innerHTML");
                        if (html != null) {
                            dto.setAplusMd5(md5Hex(html));
                        }
                    }
                } catch (WebDriverException e) {
                    log.debug("Selenium 解析 A+ 内容失败: {}", e.getMessage());
                }
            }

            // 评论总数 & 平均评分
            if (dto.getTotalReviews() == null) {
                try {
                    WebElement rev = driver.findElement(By.cssSelector("#acrCustomerReviewText"));
                    String digits = rev.getText().replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) dto.setTotalReviews(Integer.valueOf(digits));
                } catch (WebDriverException e) {
                    log.debug("Selenium 未能解析评论总数: {}", e.getMessage());
                }
            }
            if (dto.getAvgRating() == null) {
                try {
                    WebElement rating = firstPresent(driver, By.cssSelector("span[data-hook=rating-out-of-text]"), By.cssSelector("#averageCustomerReviews .a-icon-alt"));
                    if (rating != null) {
                        String txt = rating.getText().split("out")[0].replaceAll("[^0-9.]", "").trim();
                        if (!txt.isEmpty()) dto.setAvgRating(new BigDecimal(txt));
                    }
                } catch (WebDriverException | NumberFormatException e) {
                    log.debug("Selenium 解析平均评分失败: {}", e.getMessage());
                }
            }

            // 库存（999 加购法简化：若页面能找到库存相关提示，或“仅剩”提示）
            if (dto.getInventory() == null) {
                try {
                    WebElement availability = firstPresent(driver,
                            By.cssSelector("#availabilityInsideBuyBox_feature_div"),
                            By.cssSelector("#availability"),
                            By.cssSelector("#availability_feature_div"),
                            By.cssSelector("#availability-brief"),
                            By.cssSelector("#deliveryBlockMessage"),
                            By.cssSelector("#desktop_qualifiedBuyBox_feature_div"));
                    if (availability != null) {
                        String txt = availability.getText().toLowerCase(Locale.ROOT);
                        if (txt.contains("only") && txt.contains("left in stock")) {
                            String digits = txt.replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) dto.setInventory(Integer.valueOf(digits));
                        } else if (txt.contains("in stock")) {
                            dto.setInventory(null); // 标记为有货但未知数量
                        }
                    }
                } catch (WebDriverException e) {
                    log.debug("Selenium 解析库存提示失败: {}", e.getMessage());
                }
            }

            if (dto.getInventory() == null) {
                try {
                    WebElement qtyInput = firstPresent(driver,
                            By.cssSelector("input#quantity"),
                            By.cssSelector("input#mobileQuantityStepper-input"),
                            By.cssSelector("input[name=quantity]"));
                    if (qtyInput != null) {
                        String candidate = firstNonEmptyAttr(qtyInput, "data-a-max-quantity", "max", "data-max");
                        Integer parsed = parseInteger(candidate);
                        if (parsed != null) {
                            dto.setInventory(parsed);
                        }
                    }
                } catch (WebDriverException e) {
                    log.debug("Selenium 通过 quantity 输入框解析库存失败: {}", e.getMessage());
                }
            }

            // 999 加购法获取真实库存
            if (dto.getInventory() == null) {
                try {
                    log.info("ASIN: {} - 尝试通过 [999加购法] 获取真实库存", url);
                    Optional<Integer> realInventory = scrapeInventoryBy999Method(driver, url);
                    realInventory.ifPresent(dto::setInventory);
                } catch (WebDriverException e) {
                    log.error("ASIN: {} - 通过 [999加购法] 获取库存时发生错误: {}", url, e.getMessage());
                }
            }

            success = true;
            return dto;
        } catch (RuntimeException ex) {
            throw ex;
        } finally {
            if (proxy != null) {
                if (success) {
                    proxyManager.recordSuccess(proxy);
                } else {
                    proxyManager.recordFailure(proxy);
                }
            }
            borrowed.close();
        }
    }

    private WebElement firstPresent(WebDriver driver, By... selectors) {
        for (By s : selectors) {
            try {
                List<WebElement> list = driver.findElements(s);
                if (!list.isEmpty()) return list.get(0);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private BigDecimal parsePrice(String text) {
        if (text == null) return null;
        Matcher matcher = PRICE_PATTERN.matcher(text.replace('\u00a0', ' '));
        if (matcher.find()) {
            String candidate = matcher.group(1).replace(",", "");
            try {
                return new BigDecimal(candidate);
            } catch (NumberFormatException e) {
                log.debug("Selenium 解析价格失败: {}", e.getMessage());
            }
        }
        return null;
    }

    private String firstNonEmptyAttr(WebElement element, String... attrs) {
        if (element == null || attrs == null) {
            return null;
        }
        for (String attr : attrs) {
            if (attr == null) {
                continue;
            }
            String value = element.getDomAttribute(attr);
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer parseInteger(String text) {
        if (isBlank(text)) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            log.debug("Selenium 解析库存数字失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String md5Hex(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append("%02x".formatted(b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("MD5 算法不可用", e);
            return null;
        }
    }

    /**
     * V2.1 F-DATA-001: 执行“999加购法”来侦察真实库存
     * @param driver WebDriver 实例
     * @param asin 当前 ASIN，用于日志记录
     * @return Optional<Integer> 包含真实库存数量，如果无法确定则为空
     */
    private Optional<Integer> scrapeInventoryBy999Method(WebDriver driver, String asin) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // 0. 检查是否遇到验证码
            if (checkForCaptcha(driver)) {
                log.warn("ASIN: {} - 遇到验证码，无法执行 999 加购法", asin);
                return Optional.empty();
            }

            // 1. 定位并点击 "Add to Cart" 按钮
            WebElement addToCartButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("add-to-cart-button")));
            addToCartButton.click();
            log.debug("ASIN: {} - 已点击 'Add to Cart'", asin);

            // 2. 跳转到购物车页面
            // 点击加购后，有时会弹出一个侧边栏，有时会直接跳转。我们需要一个统一的入口进入购物车。
            // 最稳妥的方式是直接访问购物车URL。
            String cartUrl = "https://www.amazon.com/gp/cart/view.html";
            driver.get(cartUrl);
            log.debug("ASIN: {} - 已跳转到购物车页面: {}", asin, cartUrl);

            if (checkForCaptcha(driver)) {
                log.warn("ASIN: {} - 购物车页面遇到验证码", asin);
                return Optional.empty();
            }

            // 3. 修改购买数量为 999
            WebElement quantityInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[name^='quantity']")));
            quantityInput.clear();
            quantityInput.sendKeys("999");
            log.debug("ASIN: {} - 已将数量输入框设置为 999", asin);

            // 找到与输入框关联的更新按钮并点击
            WebElement updateButton = driver.findElement(By.cssSelector("input[data-action='update']"));
            updateButton.click();
            log.debug("ASIN: {} - 已点击数量更新按钮", asin);

            // 4. 抓取提示信息并解析
            // 等待提示信息出现。这通常是一个包含错误或提示的 div。
            // 尝试多个选择器
            WebElement alertElement = null;
            try {
                alertElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector(".sc-list-item-notification .a-alert-content, .sc-quantity-update-message .a-alert-content, .a-alert-content")));
            } catch (TimeoutException e) {
                log.warn("ASIN: {} - 未找到标准的库存提示元素，尝试查找页面上的所有警告文本", asin);
            }

            String alertText;
            if (alertElement != null) {
                alertText = alertElement.getText();
            } else {
                // Fallback: 获取整个购物车容器的文本，看是否包含相关信息
                WebElement cartContainer = driver.findElement(By.id("sc-active-cart"));
                alertText = cartContainer.getText();
            }
            
            log.info("ASIN: {} - 捕获到潜在库存提示信息: '{}'", asin, alertText.replace('\n', ' '));

            return scrapeParser.parseInventoryFromAlert(alertText);

        } catch (TimeoutException e) {
            log.warn("ASIN: {} - 执行 [999加购法] 步骤超时，可能页面结构已改变或无加购按钮: {}", asin, e.getMessage());
            // 如果在任何步骤超时，比如找不到“加到购物车”按钮，说明此方法不适用，返回空
            return Optional.empty();
        } catch (Exception e) {
            log.error("ASIN: {} - 执行 [999加购法] 时发生未预料的异常", asin, e);
            return Optional.empty();
        }
    }

    private boolean checkForCaptcha(WebDriver driver) {
        try {
            String title = driver.getTitle();
            if (title != null && (title.contains("Robot Check") || title.contains("CAPTCHA"))) {
                return true;
            }
            if (!driver.findElements(By.cssSelector("form[action*='validateCaptcha']")).isEmpty()) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * V2.1 F-BIZ-001: 关键词排名抓取（支持多页遍历，最大页数可配置）。
     * 说明：仅粗略模拟，自然排名可能受广告/置顶结果影响，生产需过滤 Sponsored。
     */
    public KeywordRankResult fetchKeywordRank(String asin, String keyword) {
        int maxPages = Math.max(1, scraperProperties.getKeywordRankMaxPages());
        return fetchKeywordRank(asin, keyword, maxPages);
    }

    public KeywordRankResult fetchKeywordRank(String asin, String keyword, int maxPages) {
        Optional<KeywordRankResult> result = computeKeywordRank(keyword, asin, Math.max(1, maxPages));
        KeywordRankResult resolved = result.orElseGet(KeywordRankResult::notFound);
        if (!resolved.isFound()) {
            log.info("关键词='{}' ASIN='{}' 在前 {} 页未找到排名", keyword, asin, maxPages);
        }
        return resolved;
    }

    private Optional<KeywordRankResult> computeKeywordRank(String keyword, String targetAsin, int maxPages) {
        if (keyword == null || keyword.isBlank() || targetAsin == null || targetAsin.isBlank()) {
            log.warn("关键词或 ASIN 为空，跳过关键词排名抓取");
            return Optional.empty();
        }

        BorrowedDriver borrowed = createDriver();
        WebDriver driver = borrowed.driver();
        ProxyInstance proxy = borrowed.proxy();
        int cumulativeOffset = 0;
        boolean success = false;
        KeywordRankResult found = null;
        try {
            for (int page = 1; page <= maxPages; page++) {
                String searchUrl = buildSearchUrl(keyword, page);
                log.info("抓取关键词='{}' 第 {} 页，URL={}", keyword, page, searchUrl);
                driver.get(searchUrl);

                if (checkForCaptcha(driver)) {
                    log.warn("关键词='{}' 第 {} 页遇到验证码，终止抓取", keyword, page);
                    break;
                }

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
                try {
                    // 兼容多种选择器，防止因页面结构差异导致误判超时
                    wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("[data-component-type='s-search-result'], div.s-result-item[data-asin]")));
                } catch (TimeoutException te) {
                    log.warn("关键词='{}' 第 {} 页加载超时，终止后续翻页", keyword, page);
                    break;
                }

                String pageSource = driver.getPageSource();
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(pageSource, driver.getCurrentUrl());

                java.util.List<org.jsoup.nodes.Element> allResults = doc.select("[data-component-type='s-search-result'][data-asin]");
                int pageCountBeforeFilter = allResults.size();
                if (scraperProperties.isFilterSponsored()) {
                    allResults = allResults.stream()
                            .filter(el -> el.select("span:containsOwn(Sponsored)").isEmpty())
                            .collect(java.util.stream.Collectors.toList());
                }
                int pageCount = allResults.size();
                log.debug("关键词='{}' 第 {} 页结果数量: {}, 过滤前: {}", keyword, page, pageCount, pageCountBeforeFilter);

                if (pageCount == 0) {
                    log.warn("关键词='{}' 第 {} 页未找到任何结果。页面标题: '{}'", keyword, page, driver.getTitle());
                    // 可能是页面结构完全改变，或者被识别为机器人但没有显示标准验证码
                }

                Optional<Integer> rankOnPageOpt = scrapeParser.parseKeywordRank(doc, targetAsin);
                if (rankOnPageOpt.isPresent()) {
                    int rankOnPage = rankOnPageOpt.get();
                    int globalRank = cumulativeOffset + rankOnPage;
                    found = new KeywordRankResult(globalRank, -1, page);
                    log.info("关键词='{}' ASIN='{}' 在第 {} 页内排名={}, 全局排名={}", keyword, targetAsin, page, rankOnPage, globalRank);
                    break;
                }

                cumulativeOffset += pageCount;

                if (page < maxPages) {
                    boolean hasNext = !doc.select("a.s-pagination-next:not(.s-pagination-disabled)").isEmpty();
                    if (!hasNext) {
                        log.debug("关键词='{}' 第 {} 页后无下一页，提前结束", keyword, page);
                        break;
                    }
                }
            }
            success = true;
            return Optional.ofNullable(found);
        } catch (Exception e) {
            log.error("抓取关键词排名时发生异常: keyword='{}', asin='{}'", keyword, targetAsin, e);
            return Optional.empty();
        } finally {
            if (proxy != null) {
                if (success) {
                    proxyManager.recordSuccess(proxy);
                } else {
                    proxyManager.recordFailure(proxy);
                }
            }
            borrowed.close();
        }
    }

    // 构建搜索 URL，区分站点域名；page=1 不加 &page 参数以模拟自然第一页
    private String buildSearchUrl(String keyword, int page) {
        String base = "https://www.amazon.com"; // 简化为美国站
        String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
        return base + "/s?k=" + encoded + (page > 1 ? "&page=" + page : "");
    }

    public static class KeywordRankResult {
        private final int naturalRank;
        private final int sponsoredRank;
        private final int page;

        public KeywordRankResult(int naturalRank, int sponsoredRank, int page) {
            this.naturalRank = naturalRank;
            this.sponsoredRank = sponsoredRank;
            this.page = page;
        }

        public static KeywordRankResult notFound() {
            return new KeywordRankResult(-1, -1, -1);
        }

        public int getNaturalRank() {
            return naturalRank;
        }

        public int getSponsoredRank() {
            return sponsoredRank;
        }

        public int getPage() {
            return page;
        }

        public boolean isFound() {
            return naturalRank > 0;
        }
    }
}
