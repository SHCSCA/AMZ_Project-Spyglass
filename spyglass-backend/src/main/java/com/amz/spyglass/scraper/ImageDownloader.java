package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ScraperProperties;
import com.amz.spyglass.scraper.ProxyManager;
import com.amz.spyglass.config.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/**
 * 图片下载与 MD5 计算器（中文注释）
 * 功能：通过 Java 11 HttpClient 在可选代理条件下下载图片字节并计算 MD5（hex 小写）。
 * 注意：下载图片会引入网络依赖，默认在配置中关闭。测试仅覆盖 MD5 计算逻辑，不做真实网络请求。
 */
@Component
public class ImageDownloader {

    private final Logger logger = LoggerFactory.getLogger(ImageDownloader.class);
    private final ProxyManager proxyManager;
    private final ScraperProperties scraperProperties;

    public ImageDownloader(ProxyManager proxyManager, ScraperProperties scraperProperties) {
        this.proxyManager = proxyManager;
        this.scraperProperties = scraperProperties;
    }

    /**
     * 下载图片并返回图片内容的 MD5（hex 小写）。如果下载失败返回 empty。
     */
    public Optional<String> downloadImageMd5(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return Optional.empty();
        if (!scraperProperties.isDownloadImageBinary()) {
            return Optional.empty();
        }

        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(scraperProperties.getImageDownloadTimeoutMs()));

            // 使用 ProxyManager 获取请求级代理（如有）并配置到 HttpClient
            try {
                ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
                if (provider != null) {
                    String proxyUrl = provider.getUrl();
                    if (proxyUrl != null && !proxyUrl.isEmpty()) {
                        String[] parts = proxyUrl.split(":" );
                        if (parts.length >= 3) {
                            String host = parts[1].replace("//", "");
                            int port = Integer.parseInt(parts[2]);
                            InetSocketAddress addr = new InetSocketAddress(host, port);
                            clientBuilder.proxy(ProxySelector.of(addr));
                        }
                    }

                    if (provider.getUsername() != null && provider.getPassword() != null) {
                        clientBuilder.authenticator(new java.net.Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(provider.getUsername(), provider.getPassword().toCharArray());
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}

            HttpClient client = clientBuilder.build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofMillis(scraperProperties.getImageDownloadTimeoutMs()))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (compatible; SpyglassBot/1.0)")
                    .build();

            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                byte[] body = resp.body();
                return Optional.of(md5Hex(body));
            } else {
                logger.warn("image download returned status {} for {}", resp.statusCode(), imageUrl);
                return Optional.empty();
            }
        } catch (IOException | InterruptedException ex) {
            logger.warn("image download failed for {}: {}", imageUrl, ex.getMessage());
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            logger.warn("unexpected error downloading image {}: {}", imageUrl, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 通过字节数组计算 MD5 十六进制字符串（小写）
     */
    public static String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
