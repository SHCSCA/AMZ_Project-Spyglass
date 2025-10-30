package com.amz.spyglass.scraper;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

/**
 * Selenium 抓取器（用于处理 JS 渲染的页面，如库存抓取）
 * 注意：运行此实现需要容器/环境中提供 Chrome + chromedriver 或者使用远程 WebDriver 服务。
 */
@Component
public class SeleniumScraper implements Scraper {

    @Override
    public String fetchTitle(String url) throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

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
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            AsinSnapshotDTO dto = new AsinSnapshotDTO();
            dto.setTitle(driver.getTitle());

            // 试图读取库存信息（site-specific, example selector）
            try {
                String invText = driver.findElement(By.cssSelector("#availability, #availability_feature_div")).getText();
                // 简化：提取数字
                String digits = invText.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) dto.setInventory(Integer.parseInt(digits));
            } catch (Exception ignored) {}

            // 其他字段交由 ScrapeParser/Jsoup 补充
            return dto;
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }
}
