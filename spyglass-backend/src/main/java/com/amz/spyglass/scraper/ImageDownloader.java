package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ScraperProperties;
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

        ProxyInstance proxy = proxyManager.borrow().orElse(null);
        boolean success = false;
        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(scraperProperties.getImageDownloadTimeoutMs()));

            if (proxy != null) {
                InetSocketAddress addr = new InetSocketAddress(proxy.getHost(), proxy.getPort());
                clientBuilder.proxy(ProxySelector.of(addr));
                if (proxy.isAuthenticationRequired()) {
                    String username = proxy.getUsername().orElse("");
                    char[] password = proxy.getPassword().map(String::toCharArray).orElse(new char[0]);
                    clientBuilder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });
                }
            }

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
                success = true;
                return Optional.of(md5Hex(body));
            } else {
                logger.warn("image download returned status {} for {}", resp.statusCode(), imageUrl);
                return Optional.empty();
            }
        } catch (InterruptedException ex) {
            logger.warn("image download interrupted for {}: {}", imageUrl, ex.getMessage());
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException ex) {
            logger.warn("image download failed for {}: {}", imageUrl, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            logger.warn("unexpected error downloading image {}: {}", imageUrl, ex.getMessage());
            return Optional.empty();
        } finally {
            if (proxy != null) {
                if (success) {
                    proxyManager.recordSuccess(proxy);
                } else {
                    proxyManager.recordFailure(proxy);
                }
            }
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
            for (byte b : digest) sb.append("%02x".formatted(b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
