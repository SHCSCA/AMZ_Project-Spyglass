package com.amz.spyglass.scraper;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Selenium 抓取器（用于处理 JS 渲染的页面，如库存抓取）
 * 注意：运行此实现需要容器/环境中提供 Chrome + chromedriver 或者使用远程 WebDriver 服务。
 */
@Component
public class SeleniumScraper implements Scraper {
    
    private static final Logger logger = LoggerFactory.getLogger(SeleniumScraper.class);
    
    @Value("${scraper.selenium.enabled:true}")
    private boolean seleniumEnabled;
    
    private final ProxyManager proxyManager;
    
    public SeleniumScraper(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }
    
    @Override
    public String fetchTitle(String url) throws Exception {
        if (!seleniumEnabled) {
            throw new IllegalStateException("Selenium scraper is disabled");
        }
        
        ChromeOptions options = buildChromeOptions();
        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            return driver.getTitle();
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        if (!seleniumEnabled) {
            throw new IllegalStateException("Selenium scraper is disabled");
        }
        
        ChromeOptions options = buildChromeOptions();
        WebDriver driver = new ChromeDriver(options);
        try {
            logger.debug("Selenium开始抓取: {}", url);
            driver.get(url);
            
            // 等待页面加载
            Thread.sleep(2000);
            
            AsinSnapshotDTO dto = new AsinSnapshotDTO();
            dto.setTitle(driver.getTitle());

            // 抓取价格
            try {
                String[] priceSelectors = {
                    "#priceblock_ourprice",
                    "#priceblock_dealprice",
                    ".a-price .a-offscreen",
                    "#corePrice_feature_div .a-price .a-offscreen"
                };
                for (String selector : priceSelectors) {
                    try {
                        WebElement priceEl = driver.findElement(By.cssSelector(selector));
                        String priceText = priceEl.getText();
                        if (priceText != null && !priceText.trim().isEmpty()) {
                            logger.debug("Selenium价格命中: {} -> {}", selector, priceText);
                            dto.setPrice(parsePriceToBigDecimal(priceText));
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                logger.debug("Selenium价格抓取失败: {}", e.getMessage());
            }

            // 抓取BSR
            try {
                WebElement bsrEl = driver.findElement(By.cssSelector("#detailBullets_feature_div, #productDetails_detailBullets_sections1"));
                String bsrText = bsrEl.getText();
                if (bsrText.contains("Best Sellers Rank")) {
                    String digits = bsrText.replaceAll("[^0-9,]", "").replaceAll(",", "");
                    if (!digits.isEmpty()) {
                        dto.setBsr(Integer.parseInt(digits.substring(0, Math.min(digits.length(), 8))));
                        logger.debug("Selenium BSR: {}", dto.getBsr());
                    }
                }
            } catch (Exception e) {
                logger.debug("Selenium BSR抓取失败: {}", e.getMessage());
            }

            // 抓取库存（简化版）
            try {
                String invText = driver.findElement(By.cssSelector("#availability, #availability_feature_div")).getText();
                logger.debug("Selenium库存文本: {}", invText);
                
                if (invText.toLowerCase().contains("in stock")) {
                    dto.setInventory(null); // 有库存但无精确数值
                } else {
                    String digits = invText.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        dto.setInventory(Integer.parseInt(digits));
                    }
                }
            } catch (Exception e) {
                logger.debug("Selenium库存抓取失败: {}", e.getMessage());
            }

            // 抓取评论总数
            try {
                WebElement reviewEl = driver.findElement(By.cssSelector("#acrCustomerReviewText"));
                String reviewText = reviewEl.getText().replaceAll("[^0-9]", "");
                if (!reviewText.isEmpty()) {
                    dto.setTotalReviews(Integer.parseInt(reviewText));
                    logger.debug("Selenium评论数: {}", dto.getTotalReviews());
                }
            } catch (Exception e) {
                logger.debug("Selenium评论数抓取失败: {}", e.getMessage());
            }

            // 抓取平均评分
            try {
                WebElement avgEl = driver.findElement(By.cssSelector("#averageCustomerReviews .a-icon-alt, #acrPopover"));
                String avgText = avgEl.getText().replaceAll("[^0-9\\.]", "");
                if (!avgText.isEmpty()) {
                    dto.setAvgRating(new java.math.BigDecimal(avgText));
                    logger.debug("Selenium评分: {}", dto.getAvgRating());
                }
            } catch (Exception e) {
                logger.debug("Selenium评分抓取失败: {}", e.getMessage());
            }

            // 抓取五点要点
            try {
                java.util.List<WebElement> bullets = driver.findElements(By.cssSelector("#feature-bullets .a-list-item, #feature-bullets li"));
                StringBuilder sb = new StringBuilder();
                for (WebElement bullet : bullets) {
                    String text = bullet.getText().trim();
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(text);
                    }
                }
                if (sb.length() > 0) {
                    dto.setBulletPoints(sb.toString());
                    logger.debug("Selenium五点要点长度: {}", dto.getBulletPoints().length());
                }
            } catch (Exception e) {
                logger.debug("Selenium五点要点抓取失败: {}", e.getMessage());
            }

            return dto;
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }
    
    /**
     * 构建ChromeOptions，包含代理配置
     */
    private ChromeOptions buildChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        
        // 添加代理支持
        try {
            com.amz.spyglass.config.ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null) {
                String proxyUrl = provider.getUrl();
                if (proxyUrl != null && !proxyUrl.isEmpty()) {
                    String[] parts = proxyUrl.split(":");
                    if (parts.length >= 3) {
                        String host = parts[1].replace("//", "");
                        String port = parts[2];
                        
                        // 如果有认证，使用格式: username:password@host:port
                        if (provider.getUsername() != null && provider.getPassword() != null) {
                            String proxyStr = String.format("%s:%s@%s:%s", 
                                provider.getUsername(), provider.getPassword(), host, port);
                            options.addArguments("--proxy-server=http://" + proxyStr);
                            logger.debug("Selenium使用代理: {}:{}@{}:{}", provider.getUsername(), "***", host, port);
                        } else {
                            options.addArguments("--proxy-server=http://" + host + ":" + port);
                            logger.debug("Selenium使用代理: {}:{}", host, port);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("设置Selenium代理失败: {}", e.getMessage());
        }
        
        return options;
    }
    
    private java.math.BigDecimal parsePriceToBigDecimal(String priceText) {
        if (priceText == null) return null;
        String cleaned = priceText.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty()) return null;
        try {
            return new java.math.BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }
}
