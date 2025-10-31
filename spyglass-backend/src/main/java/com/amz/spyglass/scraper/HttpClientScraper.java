package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyConfig;
import org.springframework.stereotype.Component;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 使用Java 11+ HttpClient的HTTP爬虫实现
 * 支持代理认证和现代HTTP特性
 */
@Component
public class HttpClientScraper implements Scraper {

    private final ProxyManager proxyManager;
    
    public HttpClientScraper(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public String fetchTitle(String url) throws Exception {
        String html = fetchContent(url);
        
        // 简单的标题提取
        int titleStart = html.indexOf("<title>");
        if (titleStart == -1) return "No Title";
        
        titleStart += 7; // "<title>".length()
        int titleEnd = html.indexOf("</title>", titleStart);
        if (titleEnd == -1) return "No Title";
        
        return html.substring(titleStart, titleEnd).trim();
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        String html = fetchContent(url);
        
        // 使用现有的ScrapeParser来解析内容
        // 需要将HTML字符串转换为Document对象
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        AsinSnapshotDTO snapshot = ScrapeParser.parse(doc);
        snapshot.setSnapshotAt(java.time.Instant.now());
        
        return snapshot;
    }
    
    private String fetchContent(String url) throws Exception {
        ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
        
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30));
        
        // 如果有代理配置，设置代理和认证
        if (provider != null) {
            String proxyHost = extractHost(provider.getUrl());
            int proxyPort = extractPort(provider.getUrl());
            
            clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
            
            // 设置代理认证
            if (provider.getUsername() != null && provider.getPassword() != null) {
                // 设置系统属性用于代理认证
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
                System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
                
                clientBuilder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new PasswordAuthentication(
                                provider.getUsername(), 
                                provider.getPassword().toCharArray()
                            );
                        }
                        return null;
                    }
                });
            }
        }
        
        HttpClient client = clientBuilder.build();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
        }
        
        return response.body();
    }
    
    private String extractHost(String url) {
        if (url == null) return "localhost";
        
        String[] parts = url.split(":");
        if (parts.length == 2) {
            return parts[0]; // host:port 格式
        } else if (parts.length >= 3) {
            return parts[1].replace("//", ""); // protocol://host:port 格式
        }
        return url;
    }
    
    private int extractPort(String url) {
        if (url == null) return 8080;
        
        String[] parts = url.split(":");
        if (parts.length == 2) {
            return Integer.parseInt(parts[1]); // host:port 格式
        } else if (parts.length >= 3) {
            return Integer.parseInt(parts[2]); // protocol://host:port 格式
        }
        return 8080;
    }
}