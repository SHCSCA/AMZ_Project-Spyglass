package com.amz.spyglass.scraper;

// 移除 WebDriverManager 以避免编译错误，需在运行环境自行保证 chromedriver 可用
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.Proxy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Selenium 抓取器（用于处理 JS 渲染的页面，如库存 / 动态加载价格 / 评价等）。
 * 说明：用于补充 Jsoup 静态抓取无法获取或为空的字段。
 */
@Slf4j
@Component
public class SeleniumScraper implements Scraper {

    private final ProxyManager proxyManager;

    public SeleniumScraper(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    private ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        // 注入代理（如果启用）
        try {
            com.amz.spyglass.config.ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null && provider.getUrl() != null && !provider.getUrl().isEmpty()) {
                String[] parts = provider.getUrl().split(":");
                String host;
                int port;
                if (parts.length == 2) {
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else if (parts.length >= 3) {
                    host = parts[1].replace("//", "");
                    port = Integer.parseInt(parts[2]);
                } else {
                    throw new IllegalArgumentException("Invalid proxy URL format: " + provider.getUrl());
                }
                // Selenium 使用 ChromeOptions 添加代理参数
                options.addArguments("--proxy-server=" + host + ":" + port);
                if (provider.getUsername() != null && provider.getPassword() != null) {
                    log.warn("[Selenium] 当前使用的HTTP代理需要用户名密码，Chrome原生不直接支持Basic认证，需要在后续实现扩展(如使用Selenium扩展或启动后注入认证)。用户名={}", provider.getUsername());
                }
            }
        } catch (Exception e) {
            log.warn("[Selenium] 代理配置失败: {}", e.getMessage());
        }
        return options;
    }

    private WebDriver createDriver() {
        // 假设 chromedriver 已在 PATH 中或由系统驱动映射
        return new ChromeDriver(buildOptions());
    }

    @Override
    public String fetchTitle(String url) { // 去掉 throws Exception
        WebDriver driver = createDriver();
        try {
            driver.get(url);
            return driver.getTitle();
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) { // 去掉 throws Exception
        WebDriver driver = createDriver();
        AsinSnapshotDTO dto = new AsinSnapshotDTO();
        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            try { wait.until(d -> ((JavascriptExecutor)d).executeScript("return document.readyState").equals("complete")); } catch (Exception ignored) {}
            dto.setTitle(driver.getTitle());

            // 价格
            if (dto.getPrice() == null) {
                try {
                    WebElement priceEl = firstPresent(driver,
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
                            if (!digits.isEmpty()) { dto.setBsr(Integer.parseInt(digits)); break; }
                        }
                    }
                    if (dto.getBsr() == null) {
                        try {
                            WebElement sr = driver.findElement(By.cssSelector("#SalesRank"));
                            String digits = sr.getText().replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) dto.setBsr(Integer.parseInt(digits));
                        } catch (Exception ignoredInner) {}
                    }
                } catch (Exception ignored) {}
            }

            // 五点要点
            if (dto.getBulletPoints() == null) {
                try {
                    List<WebElement> bullets = driver.findElements(By.cssSelector("#feature-bullets li"));
                    StringBuilder sb = new StringBuilder();
                    for (WebElement b : bullets) {
                        String t = b.getText().trim();
                        if (t.isEmpty()) continue;
                        if (!sb.isEmpty()) sb.append('\n');
                        sb.append(t);
                    }
                    if (!sb.isEmpty()) dto.setBulletPoints(sb.toString());
                } catch (Exception ignored) {}
            }

            // 主图 MD5 （基于 URL）
            if (dto.getImageMd5() == null) {
                try {
                    WebElement img = firstPresent(driver,
                            By.cssSelector("#landingImage"), By.cssSelector("#imgTagWrapperId img"), By.cssSelector("img#main-image"));
                    if (img != null) {
                        String src = img.getAttribute("src");
                        if (src != null && !src.isEmpty()) dto.setImageMd5(md5Hex(src));
                    }
                } catch (Exception ignored) {}
            }

            // A+ 内容 MD5
            if (dto.getAplusMd5() == null) {
                try {
                    WebElement aplus = firstPresent(driver, By.cssSelector("#aplus"), By.cssSelector(".aplus"), By.cssSelector(".a-plus"));
                    if (aplus != null) dto.setAplusMd5(md5Hex(aplus.getAttribute("innerHTML")));
                } catch (Exception ignored) {}
            }

            // 评论总数 & 平均评分
            if (dto.getTotalReviews() == null) {
                try {
                    WebElement rev = driver.findElement(By.cssSelector("#acrCustomerReviewText"));
                    String digits = rev.getText().replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) dto.setTotalReviews(Integer.parseInt(digits));
                } catch (Exception ignored) {}
            }
            if (dto.getAvgRating() == null) {
                try {
                    WebElement rating = firstPresent(driver, By.cssSelector("span[data-hook=rating-out-of-text]"), By.cssSelector("#averageCustomerReviews .a-icon-alt"));
                    if (rating != null) {
                        String txt = rating.getText().split("out")[0].replaceAll("[^0-9.]", "").trim();
                        if (!txt.isEmpty()) dto.setAvgRating(new BigDecimal(txt));
                    }
                } catch (Exception ignored) {}
            }

            // 库存（999 加购法简化：若页面能找到库存相关提示，或“仅剩”提示）
            if (dto.getInventory() == null) {
                try {
                    WebElement availability = firstPresent(driver,
                            By.cssSelector("#availability"),
                            By.cssSelector("#availability_feature_div"),
                            By.cssSelector("#desktop_qualifiedBuyBox_feature_div"));
                    if (availability != null) {
                        String txt = availability.getText().toLowerCase(Locale.ROOT);
                        if (txt.contains("only") && txt.contains("left in stock")) {
                            String digits = txt.replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) dto.setInventory(Integer.parseInt(digits));
                        } else if (txt.contains("in stock")) {
                            dto.setInventory(null); // 标记为有货但未知数量
                        }
                    }
                } catch (Exception ignored) {}
            }

        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
        return dto;
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
        String cleaned = text.replaceAll("[^0-9.\\-]", ""); // 修正正则
        if (cleaned.isEmpty()) return null;
        try { return new BigDecimal(cleaned); } catch (Exception e) { return null; }
    }

    private String md5Hex(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
