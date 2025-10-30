package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ScraperProperties;
import com.amz.spyglass.scraper.ProxyManager;
import java.util.Optional;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Jsoup 实现的 Scraper（中文注释）
 * 说明：此实现使用 Jsoup 发起请求，并在配置了代理时尝试通过代理发送请求。
 * 注意：对需要代理鉴权的场景，Jsoup 原生对代理鉴权支持有限，目前实现使用 JVM 全局 Authenticator 作为简易方案。
 */
@Component
public class JsoupScraper implements Scraper {

    private final Logger logger = LoggerFactory.getLogger(JsoupScraper.class);
    private final ProxyManager proxyManager;
    private final ScraperProperties scraperProperties;
    private final com.amz.spyglass.scraper.ImageDownloader imageDownloader;
    public JsoupScraper(ProxyManager proxyManager, ScraperProperties scraperProperties, com.amz.spyglass.scraper.ImageDownloader imageDownloader) {
        this.proxyManager = proxyManager;
        this.scraperProperties = scraperProperties;
        this.imageDownloader = imageDownloader;
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
        long startMs = System.currentTimeMillis();
        // 随机延迟（降低访问模式特征）
        try {
            int min = scraperProperties.getRandomDelayMinMs();
            int max = scraperProperties.getRandomDelayMaxMs();
            int delay = min + (int)(Math.random() * Math.max(1, (max - min)));
            Thread.sleep(delay);
            logger.debug("[Jsoup] 随机延迟 {} ms 后开始抓取 url={}", delay, url);
        } catch (InterruptedException ignored) {}

        AsinSnapshotDTO snapshot = null;
        Exception lastEx = null;
        for (int attempt = 1; attempt <= scraperProperties.getMaxRetry(); attempt++) {
            try {
                Connection conn = Jsoup.connect(url)
                        .userAgent(randomUserAgent())
                        .timeout((int) Duration.ofSeconds(30).toMillis())
                        .followRedirects(true)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1");
                // 代理
                applyProxy(conn);
                Document doc = conn.get();
                snapshot = ScrapeParser.parse(doc);
                snapshot.setSnapshotAt(java.time.Instant.now());

                boolean antiBot = isAntiBotPage(doc);
                boolean criticalMissing = snapshot.getPrice() == null && snapshot.getBsr() == null; // 关键指标皆空
                if (antiBot || criticalMissing) {
                    logger.warn("[Jsoup] 可能命中防爬或关键字段缺失 (antiBot={}, criticalMissing={}) attempt={} url={}", antiBot, criticalMissing, attempt, url);
                    dumpHtmlIfEnabled(doc, url, "attempt" + attempt + (antiBot ? "-antibot" : ""));
                }
                if (!criticalMissing) {
                    break; // 已有部分关键数据，不再重试
                }
            } catch (Exception ex) {
                lastEx = ex;
                logger.warn("[Jsoup] 抓取异常 attempt={} url={} msg={}", attempt, url, ex.getMessage());
                if (attempt == scraperProperties.getMaxRetry()) throw ex; // 最后一次抛出
                // 继续重试
            }
        }

        if (snapshot == null) throw lastEx != null ? lastEx : new IllegalStateException("抓取失败且无异常信息 url=" + url);

        logger.info("[Jsoup] 抓取完成 summary: title='{}' price={} bsr={} inventory={} reviews={} rating={} imageMd5={} aplusMd5={} cost={}ms",
                truncate(snapshot.getTitle(),60), snapshot.getPrice(), snapshot.getBsr(), snapshot.getInventory(), snapshot.getTotalReviews(), snapshot.getAvgRating(), snapshot.getImageMd5(), snapshot.getAplusMd5(), (System.currentTimeMillis()-startMs));
        return snapshot;
    }

    private void applyProxy(Connection conn) {
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
                        logger.debug("[Jsoup] 使用代理 host={} port={}", host, port);
                    }
                }
                if (provider.getUsername() != null && provider.getPassword() != null) {
                    String auth = provider.getUsername() + ":" + provider.getPassword();
                    String encoded = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    conn.header("Proxy-Authorization", "Basic " + encoded);
                }
            }
        } catch (Exception e) {
            logger.warn("[Jsoup] 代理配置应用失败: {}", e.getMessage());
        }
    }

    private boolean isAntiBotPage(Document doc) {
        return doc.selectFirst("form[action*=/errors/validateCaptcha]") != null
                || doc.title().toLowerCase().contains("robot check")
                || doc.text().toLowerCase().contains("enter the characters you see below");
    }

    private void dumpHtmlIfEnabled(Document doc, String url, String tag) {
        if (!scraperProperties.isHtmlDumpEnabled()) return;
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(scraperProperties.getHtmlDumpDir());
            java.nio.file.Files.createDirectories(dir);
            String safeName = url.replaceAll("[^a-zA-Z0-9]", "_");
            java.nio.file.Path file = dir.resolve(safeName + "_" + tag + "_" + System.currentTimeMillis() + ".html");
            java.nio.file.Files.writeString(file, doc.outerHtml());
            logger.debug("[Jsoup] 已保存 HTML dump -> {}", file.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("[Jsoup] HTML dump 失败: {}", e.getMessage());
        }
    }

    // 简单的 UA 轮换（可扩展为从配置读取更大池）
    private String randomUserAgent() {
        String[] uas = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0"
        };
        return uas[(int)(Math.random()*uas.length)];
    }

    private String truncate(String input, int max) {
        if (input == null) return null;
        return input.length() <= max ? input : input.substring(0,max)+"...";
    }
}
