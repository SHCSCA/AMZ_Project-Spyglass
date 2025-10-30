package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyProperties;
import com.amz.spyglass.config.ScraperProperties;
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

    private final ProxyProperties proxyProperties;
    private final ScraperProperties scraperProperties;
    private final com.amz.spyglass.scraper.ImageDownloader imageDownloader;

    public JsoupScraper(ProxyProperties proxyProperties, ScraperProperties scraperProperties, com.amz.spyglass.scraper.ImageDownloader imageDownloader) {
        this.proxyProperties = proxyProperties;
        this.scraperProperties = scraperProperties;
        this.imageDownloader = imageDownloader;
    }

    @Override
    public String fetchTitle(String url) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SpyglassBot/1.0)")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true);

        // 如果配置了代理，则设置代理
        if (proxyProperties != null && proxyProperties.isEnabled() && proxyProperties.getHost() != null && proxyProperties.getPort() != null) {
            conn.proxy(proxyProperties.getHost(), proxyProperties.getPort());

            // 简单处理代理鉴权：通过 JVM 全局 Authenticator 提供用户名/密码
            if (proxyProperties.getUsername() != null && proxyProperties.getPassword() != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxyProperties.getUsername(), proxyProperties.getPassword().toCharArray());
                    }
                });
            }
        }

        Document doc = conn.get();
        return doc.title();
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; SpyglassBot/1.0)")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true);

        if (proxyProperties != null && proxyProperties.isEnabled() && proxyProperties.getHost() != null && proxyProperties.getPort() != null) {
            conn.proxy(proxyProperties.getHost(), proxyProperties.getPort());
            if (proxyProperties.getUsername() != null && proxyProperties.getPassword() != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxyProperties.getUsername(), proxyProperties.getPassword().toCharArray());
                    }
                });
            }
        }

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
