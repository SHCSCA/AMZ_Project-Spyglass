package com.amz.spyglass.scraper;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.amz.spyglass.config.ScraperProperties;

/**
 * Jsoup 实现的 Scraper（中文注释）
 * 说明：此实现使用 Jsoup 发起请求，并在配置了代理时通过 {@link ProxyManager} 注入代理与鉴权头。
 */
@Component
public class JsoupScraper implements Scraper {

    private final Logger logger = LoggerFactory.getLogger(JsoupScraper.class);
    private final ProxyManager proxyManager;
    private final ScraperProperties scraperProperties;
    public JsoupScraper(ProxyManager proxyManager, ScraperProperties scraperProperties) {
        this.proxyManager = proxyManager;
        this.scraperProperties = scraperProperties;
    }

    @Override
    public String fetchTitle(String url) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true);

        ProxyInstance proxy = attachProxy(conn).orElse(null);
        try {
            Document doc = conn.get();
            proxyManager.recordSuccess(proxy);
            return doc.title();
        } catch (IOException | RuntimeException ex) {
            proxyManager.recordFailure(proxy);
            throw ex;
        }
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        long startMs = System.currentTimeMillis();
        // 随机延迟（降低访问模式特征）
        int min = scraperProperties.getRandomDelayMinMs();
        int max = scraperProperties.getRandomDelayMaxMs();
        int delay = min + (int) (ThreadLocalRandom.current().nextDouble() * Math.max(1, (max - min)));
        sleepQuietly(delay);
        logger.debug("[Jsoup] 随机延迟 {} ms 后开始抓取 url={}", delay, url);

        AsinSnapshotDTO snapshot = null;
        Exception lastEx = null;
        for (int attempt = 1; attempt <= scraperProperties.getMaxRetry(); attempt++) {
            try {
                // 使用 Apache HttpClient 替代 Jsoup.connect
                String html = fetchHtmlWithHttpClient(url);
                Document doc = Jsoup.parse(html, url);
                
                snapshot = ScrapeParser.parse(doc.html(), url);
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

                long backoff = (long) Math.min(4000, 1000 * Math.pow(2, attempt - 1));
                sleepQuietly(backoff);
            } catch (Exception ex) {
                lastEx = ex;
                logger.warn("[Jsoup] 抓取异常 attempt={} url={} msg={}", attempt, url, ex.getMessage());
                if (attempt == scraperProperties.getMaxRetry()) {
                    // throw ex; // Will be thrown after loop
                }
                long backoff = (long) Math.min(4000, 1000 * Math.pow(2, attempt - 1));
                sleepQuietly(backoff);
            }
        }

        if (snapshot == null) {
            throw lastEx != null ? lastEx : new IllegalStateException("抓取失败且无异常信息 url=" + url);
        }

        logger.info("[Jsoup] 抓取完成 summary: title='{}' price={} bsr={} reviews={} rating={} cost={}ms",
                truncate(snapshot.getTitle(), 60), snapshot.getPrice(), snapshot.getBsr(), snapshot.getTotalReviews(), snapshot.getAvgRating(), (System.currentTimeMillis() - startMs));
        return snapshot;
    }

    public Optional<Document> getDocument(String url) {
        try {
            // 使用 Apache HttpClient 替代 Jsoup.connect 以解决 407 代理认证问题
            String html = fetchHtmlWithHttpClient(url);
            return Optional.of(Jsoup.parse(html, url));
        } catch (Exception e) {
            logger.error("Error fetching document from {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private String fetchHtmlWithHttpClient(String url) throws Exception {
        ProxyInstance proxyInstance = proxyManager.borrow()
                .orElseThrow(() -> new IllegalStateException("未配置可用代理"));

        String proxyHost = proxyInstance.getHost();
        int proxyPort = proxyInstance.getPort();
        
        logger.info("[Jsoup-HttpClient] 使用代理: {}:{} 用户: {}", proxyHost, proxyPort, proxyInstance.getUsername().orElse("<none>"));

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        proxyInstance.getUsername().ifPresent(username -> {
            char[] password = proxyInstance.getPassword().map(String::toCharArray).orElse(new char[0]);
            credsProvider.setCredentials(new AuthScope(proxy), new UsernamePasswordCredentials(username, password));
        });

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setRoutePlanner(new DefaultProxyRoutePlanner(proxy))
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet request = new HttpGet(url);
            // 使用与 HttpClientScraper 完全一致的 Header
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
            request.setHeader("Accept-Language", "en-US,en;q=0.9");
            request.setHeader("Accept-Encoding", "gzip, deflate, br");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("Upgrade-Insecure-Requests", "1");
            request.setHeader("Sec-Fetch-Dest", "document");
            
            return httpClient.execute(request, response -> {
                return EntityUtils.toString(response.getEntity());
            });
        } finally {
            // 简单记录代理使用情况（不区分成功失败，因为异常会抛出）
            proxyManager.recordSuccess(proxyInstance);
        }
    }

    private Optional<ProxyInstance> attachProxy(Connection conn) {
        Optional<ProxyInstance> borrowed = proxyManager.borrow();
        borrowed.ifPresent(proxy -> {
            conn.proxy(proxy.getHost(), proxy.getPort());
            String header = proxy.buildProxyHeaderValue();
            if (header != null) {
                conn.header("Proxy-Authorization", header);
            }
        });
        return borrowed;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
        } catch (IOException e) {
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
        return uas[(int)(ThreadLocalRandom.current().nextDouble()*uas.length)];
    }

    private String truncate(String input, int max) {
        if (input == null) return null;
        return input.length() <= max ? input : input.substring(0,max)+"...";
    }
}
