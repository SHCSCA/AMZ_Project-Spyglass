package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ScraperProperties;
import com.amz.spyglass.scraper.ProxyManager;
import java.util.Optional;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Jsoup 实现的 Scraper（中文注释）
 * 说明：此实现使用 Jsoup 发起请求，并在配置了代理时尝试通过代理发送请求。
 * 注意：对需要代理鉴权的场景，Jsoup 原生对代理鉴权支持有限，目前实现使用 JVM 全局 Authenticator 作为简易方案。
 */
@Component
public class JsoupScraper implements Scraper {

    private static final Logger logger = LoggerFactory.getLogger(JsoupScraper.class);
    private static final Random random = new Random();
    private static final String HTML_DUMP_DIR = "logs/html-dump";
    
    private final ProxyManager proxyManager;
    private final ScraperProperties scraperProperties;
    private final com.amz.spyglass.scraper.ImageDownloader imageDownloader;
    
    public JsoupScraper(ProxyManager proxyManager, ScraperProperties scraperProperties, com.amz.spyglass.scraper.ImageDownloader imageDownloader) {
        this.proxyManager = proxyManager;
        this.scraperProperties = scraperProperties;
        this.imageDownloader = imageDownloader;
        
        // 确保HTML dump目录存在
        File dumpDir = new File(HTML_DUMP_DIR);
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }
    }

    @Override
    public String fetchTitle(String url) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SpyglassBot/1.0)")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true);

        // 如果配置了代理，则从 ProxyManager 获取下一个代理并应用到请求（线程安全的请求级代理认证）
        try {
            com.amz.spyglass.config.ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null) {
                // 解析 host:port
                String proxyUrl = provider.getUrl();
                if (proxyUrl != null && !proxyUrl.isEmpty()) {
                    String[] parts = proxyUrl.split(":");
                    if (parts.length >= 3) {
                        String host = parts[1].replace("//", "");
                        int port = Integer.parseInt(parts[2]);
                        conn.proxy(host, port);
                    }
                }

                // 如果有认证，添加请求级 Proxy-Authorization 头（Base64）
                if (provider.getUsername() != null && provider.getPassword() != null) {
                    String auth = provider.getUsername() + ":" + provider.getPassword();
                    String encoded = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    conn.header("Proxy-Authorization", "Basic " + encoded);
                }
            }
        } catch (Exception ignored) {}

        Document doc = conn.get();
        return doc.title();
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        // 添加随机延迟（150-500ms）降低访问模式特征
        int delayMs = 150 + random.nextInt(351); // 150-500ms
        logger.debug("抓取前随机延迟: {}ms", delayMs);
        Thread.sleep(delayMs);
        
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true);

        // 使用 ProxyManager 获取请求级代理并应用（不使用 JVM 全局 Authenticator）
        try {
            com.amz.spyglass.config.ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null) {
                String proxyUrl = provider.getUrl();
                if (proxyUrl != null && !proxyUrl.isEmpty()) {
                    String[] parts = proxyUrl.split(":" );
                    if (parts.length >= 3) {
                        String host = parts[1].replace("//", "");
                        int port = Integer.parseInt(parts[2]);
                        conn.proxy(host, port);
                        logger.debug("使用代理: {}:{}", host, port);
                    }
                }
                if (provider.getUsername() != null && provider.getPassword() != null) {
                    String auth = provider.getUsername() + ":" + provider.getPassword();
                    String encoded = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    conn.header("Proxy-Authorization", "Basic " + encoded);
                }
            }
        } catch (Exception ignored) {}

        Document doc = conn.get();
        
        // 检测防爬页面
        String bodyText = doc.body().text().toLowerCase();
        boolean isCaptcha = bodyText.contains("robot") || bodyText.contains("captcha") || bodyText.contains("sorry");
        if (isCaptcha) {
            logger.warn("检测到防爬页面，保存HTML用于分析: {}", url);
            dumpHtml(doc, url, "captcha");
        }
        
        AsinSnapshotDTO s = ScrapeParser.parse(doc);
        
        // 如果关键字段全部为null，保存HTML便于线下分析
        boolean allFieldsNull = s.getPrice() == null && s.getBsr() == null && 
                                s.getInventory() == null && s.getTotalReviews() == null && 
                                s.getAvgRating() == null;
        if (allFieldsNull) {
            logger.warn("所有关键字段均为null，保存HTML用于分析: {}", url);
            dumpHtml(doc, url, "all-null");
        }
        s.setSnapshotAt(java.time.Instant.now());

        // 如果启用了二进制图片下载，则尝试下载图片并计算真实 MD5，失败时回退到 URL 的 MD5（由解析器已计算）
        if (scraperProperties != null && scraperProperties.isDownloadImageBinary()) {
            String imgUrl = null;
            // 从解析结果或 DOM 再取一次主图 URL
            // 解析器可能已把 imageMd5 填为 URL 的 MD5；这里我们尝试从 DOM 获取 src
            try {
                org.jsoup.nodes.Element img = doc.selectFirst("#landingImage, #imgTagWrapperId img, img#main-image, img#image-block img");
                if (img != null) imgUrl = img.attr("src");
            } catch (Exception ignored) {}

            if (imgUrl != null && !imgUrl.isEmpty()) {
                try {
                    Optional<String> realMd5 = imageDownloader.downloadImageMd5(imgUrl);
                    realMd5.ifPresent(s::setImageMd5);
                } catch (Exception e) {
                    // ignore and keep existing imageMd5
                }
            }
        }

        return s;
    }
    
    /**
     * 将HTML内容保存到文件用于调试分析
     */
    private void dumpHtml(Document doc, String url, String reason) {
        try {
            String timestamp = Instant.now().toString().replaceAll(":", "-");
            String fileName = String.format("%s_%s_%d.html", reason, timestamp, System.currentTimeMillis() % 10000);
            File file = new File(HTML_DUMP_DIR, fileName);
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("<!-- URL: " + url + " -->\n");
                writer.write("<!-- Reason: " + reason + " -->\n");
                writer.write("<!-- Timestamp: " + Instant.now() + " -->\n");
                writer.write(doc.outerHtml());
            }
            
            logger.info("HTML已保存至: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("保存HTML失败: {}", e.getMessage());
        }
    }
}
