package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ScraperProperties;
import com.amz.spyglass.scraper.ProxyManager;
import java.util.Optional;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.time.Duration;

/**
 * Jsoup 实现的 Scraper（中文注释）
 * 说明：此实现使用 Jsoup 发起请求，并在配置了代理时尝试通过代理发送请求。
 * 注意：对需要代理鉴权的场景，Jsoup 原生对代理鉴权支持有限，目前实现使用 JVM 全局 Authenticator 作为简易方案。
 */
@Component
public class JsoupScraper implements Scraper {

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

        // 如果配置了代理，则从 ProxyManager 获取下一个代理并应用到请求
        try {
            com.amz.spyglass.config.ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null) {
                // 解析 host:port
                String proxyUrl = provider.getUrl();
                if (proxyUrl != null && !proxyUrl.isEmpty()) {
                    String[] parts = proxyUrl.split(":");
                    String host;
                    int port;
                    
                    if (parts.length == 2) {
                        // 格式: host:port
                        host = parts[0];
                        port = Integer.parseInt(parts[1]);
                    } else if (parts.length >= 3) {
                        // 格式: protocol://host:port
                        host = parts[1].replace("//", "");
                        port = Integer.parseInt(parts[2]);
                    } else {
                        throw new IllegalArgumentException("Invalid proxy URL format: " + proxyUrl);
                    }
                    
                    conn.proxy(host, port);
                    
                    // 对于需要认证的代理，使用系统级认证器
                    if (provider.getUsername() != null && provider.getPassword() != null) {
                        System.setProperty("http.proxyUser", provider.getUsername());
                        System.setProperty("http.proxyPassword", provider.getPassword());
                        System.setProperty("https.proxyUser", provider.getUsername());
                        System.setProperty("https.proxyPassword", provider.getPassword());
                        
                        // 设置认证器
                        Authenticator.setDefault(new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(
                                    provider.getUsername(), 
                                    provider.getPassword().toCharArray()
                                );
                            }
                        });
                    }
                }
            }
        } catch (Exception ignored) {}

        Document doc = conn.get();
        return doc.title();
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SpyglassBot/1.0)")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true);

        // 使用 ProxyManager 获取代理并应用
        try {
            com.amz.spyglass.config.ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
            if (provider != null) {
                String proxyUrl = provider.getUrl();
                if (proxyUrl != null && !proxyUrl.isEmpty()) {
                    String[] parts = proxyUrl.split(":");
                    String host;
                    int port;
                    
                    if (parts.length == 2) {
                        // 格式: host:port
                        host = parts[0];
                        port = Integer.parseInt(parts[1]);
                    } else if (parts.length >= 3) {
                        // 格式: protocol://host:port
                        host = parts[1].replace("//", "");
                        port = Integer.parseInt(parts[2]);
                    } else {
                        throw new IllegalArgumentException("Invalid proxy URL format: " + proxyUrl);
                    }
                    
                    conn.proxy(host, port);
                    
                    // 对于需要认证的代理，使用系统级认证器
                    if (provider.getUsername() != null && provider.getPassword() != null) {
                        System.setProperty("http.proxyUser", provider.getUsername());
                        System.setProperty("http.proxyPassword", provider.getPassword());
                        System.setProperty("https.proxyUser", provider.getUsername());
                        System.setProperty("https.proxyPassword", provider.getPassword());
                        
                        // 设置认证器（如果尚未设置）
                        if (Authenticator.getDefault() == null) {
                            Authenticator.setDefault(new Authenticator() {
                                @Override
                                protected PasswordAuthentication getPasswordAuthentication() {
                                    return new PasswordAuthentication(
                                        provider.getUsername(), 
                                        provider.getPassword().toCharArray()
                                    );
                                }
                            });
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        Document doc = conn.get();
            AsinSnapshotDTO s = ScrapeParser.parse(doc);
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
}
