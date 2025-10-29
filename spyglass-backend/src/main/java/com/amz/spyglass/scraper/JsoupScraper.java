package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyProperties;
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

    public JsoupScraper(ProxyProperties proxyProperties) {
        this.proxyProperties = proxyProperties;
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
    public AsinSnapshot fetchSnapshot(String url) throws Exception {
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
        AsinSnapshot s = ScrapeParser.parse(doc);
        s.setSnapshotAt(java.time.Instant.now());
        return s;
    }
}
