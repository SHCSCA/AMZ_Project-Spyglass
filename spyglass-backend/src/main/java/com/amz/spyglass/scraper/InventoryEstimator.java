package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 库存精确估算器
 * 使用"999加购法"通过Selenium模拟加入购物车，修改数量来估算真实库存
 */
@Component
public class InventoryEstimator {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryEstimator.class);
    
    @Value("${scraper.inventory.estimator.enabled:false}")
    private boolean enabled;
    
    private final ProxyManager proxyManager;
    
    public InventoryEstimator(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }
    
    /**
     * 使用999加购法估算库存
     * 
     * @param asin 商品ASIN
     * @param site 站点（如US）
     * @return 估算的库存数量，如果失败返回null
     */
    public Integer estimateInventory(String asin, String site) {
        if (!enabled) {
            logger.debug("库存估算器未启用");
            return null;
        }
        
        String url = String.format("https://www.amazon.%s/dp/%s", 
            site.equalsIgnoreCase("US") ? "com" : site.toLowerCase(), asin);
        
        ChromeOptions options = buildChromeOptions();
        WebDriver driver = new ChromeDriver(options);
        
        try {
            logger.info("开始999加购法库存估算: {}", asin);
            driver.get(url);
            
            // 等待页面加载
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // 查找"加入购物车"按钮
            try {
                WebElement addToCartBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#add-to-cart-button, #buy-now-button, input[name='submit.add-to-cart']")));
                
                // 点击加入购物车
                addToCartBtn.click();
                logger.debug("已点击加入购物车");
                
                // 等待购物车页面或弹窗
                Thread.sleep(2000);
                
                // 尝试导航到购物车
                try {
                    driver.get("https://www.amazon." + (site.equalsIgnoreCase("US") ? "com" : site.toLowerCase()) + "/gp/cart/view.html");
                    Thread.sleep(1000);
                } catch (Exception e) {
                    logger.debug("导航到购物车失败: {}", e.getMessage());
                }
                
                // 查找数量输入框
                WebElement qtyInput = driver.findElement(By.cssSelector(
                    "input[name='quantityBox'], .quantity input, input[aria-label*='Quantity']"));
                
                // 清空并输入999
                qtyInput.clear();
                qtyInput.sendKeys("999");
                
                // 提交更新（可能是点击更新按钮或直接触发change事件）
                try {
                    WebElement updateBtn = driver.findElement(By.cssSelector(
                        "input[value='Update'], .a-button-text[data-action='update'], span.sc-update-link"));
                    updateBtn.click();
                } catch (Exception e) {
                    // 如果没有更新按钮，尝试触发change事件
                    qtyInput.sendKeys(Keys.ENTER);
                }
                
                // 等待页面响应
                Thread.sleep(2000);
                
                // 检查是否有错误提示或数量被自动修正
                try {
                    // 亚马逊通常会显示"This seller has only X available"的消息
                    WebElement errorMsg = driver.findElement(By.cssSelector(
                        ".sc-list-item-error, .a-alert-error, .a-alert-warning"));
                    String errorText = errorMsg.getText();
                    logger.debug("购物车错误提示: {}", errorText);
                    
                    // 提取数字（通常是"This seller has only 15 available"）
                    String digits = errorText.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        int inventory = Integer.parseInt(digits);
                        logger.info("999加购法估算库存成功: {} -> {}", asin, inventory);
                        return inventory;
                    }
                } catch (Exception e) {
                    logger.debug("未找到错误提示，尝试读取修正后的数量");
                }
                
                // 如果没有错误提示，读取实际设置的数量（亚马逊可能自动修正）
                try {
                    String actualQty = qtyInput.getAttribute("value");
                    if (actualQty != null && !actualQty.isEmpty() && !actualQty.equals("999")) {
                        int inventory = Integer.parseInt(actualQty);
                        logger.info("999加购法读取修正数量: {} -> {}", asin, inventory);
                        return inventory;
                    }
                } catch (Exception e) {
                    logger.debug("读取数量失败: {}", e.getMessage());
                }
                
                // 如果成功设置999且无错误，说明库存>=999
                logger.info("库存充足(>=999): {}", asin);
                return 999;
                
            } catch (Exception e) {
                logger.warn("999加购法失败 ASIN {}: {}", asin, e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            logger.error("库存估算异常 ASIN {}: {}", asin, e.getMessage());
            return null;
        } finally {
            try {
                // 清空购物车（避免影响下次测试）
                try {
                    WebElement deleteLink = driver.findElement(By.cssSelector(
                        "input[value='Delete'], .sc-action-delete"));
                    deleteLink.click();
                    Thread.sleep(500);
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
            
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
            ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null) {
                String proxyUrl = provider.getUrl();
                if (proxyUrl != null && !proxyUrl.isEmpty()) {
                    String[] parts = proxyUrl.split(":");
                    if (parts.length >= 3) {
                        String host = parts[1].replace("//", "");
                        String port = parts[2];
                        
                        if (provider.getUsername() != null && provider.getPassword() != null) {
                            String proxyStr = String.format("%s:%s@%s:%s", 
                                provider.getUsername(), provider.getPassword(), host, port);
                            options.addArguments("--proxy-server=http://" + proxyStr);
                        } else {
                            options.addArguments("--proxy-server=http://" + host + ":" + port);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("设置代理失败: {}", e.getMessage());
        }
        
        return options;
    }
}
