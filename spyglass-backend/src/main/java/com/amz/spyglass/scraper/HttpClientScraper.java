package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * HttpClient抓取器 - 使用Apache HttpClient 5支持主动代理认证(Preemptive Proxy Auth)
 * 解决Novproxy需要在CONNECT阶段就发送Proxy-Authorization header的问题
 */
@Slf4j
@Component
public class HttpClientScraper implements Scraper {

    @Autowired
    private ProxyManager proxyManager;

    @Override
    public String fetchTitle(String url) throws Exception {
        String html = fetchWithProxy(url);
        Document doc = Jsoup.parse(html);
        
        // 提取标题
        int titleStart = html.indexOf("<title>");
        if (titleStart == -1) return "No Title";
        
        titleStart += 7;
        int titleEnd = html.indexOf("</title>", titleStart);
        if (titleEnd == -1) return "No Title";
        
        return html.substring(titleStart, titleEnd).trim();
    }

    @Override
    public AsinSnapshotDTO fetchSnapshot(String url) throws Exception {
        log.debug("[HttpClient] 开始抓取: {}", url);
        String html = fetchWithProxy(url);
        Document doc = Jsoup.parse(html);
        AsinSnapshotDTO snapshot = ScrapeParser.parse(doc);
        snapshot.setSnapshotAt(java.time.Instant.now());
        return snapshot;
    }

    /**
     * 使用Apache HttpClient 5 + 主动代理认证抓取HTML
     */
    private String fetchWithProxy(String url) throws Exception {
        ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
        
        if (provider == null || provider.getUrl() == null) {
            throw new RuntimeException("未配置代理");
        }
        
        String proxyHost = extractHost(provider.getUrl());
        int proxyPort = extractPort(provider.getUrl());
        
        log.debug("[HttpClient] 使用代理: {}:{} 用户: {}", proxyHost, proxyPort, provider.getUsername());
        
        // 创建代理主机
        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
        
        // 配置主动代理认证(Preemptive Authentication)
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        if (provider.getUsername() != null && provider.getPassword() != null) {
            credsProvider.setCredentials(
                new AuthScope(proxy),
                new UsernamePasswordCredentials(
                    provider.getUsername(), 
                    provider.getPassword().toCharArray()
                )
            );
        }
        
        // 配置请求超时
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.of(30, TimeUnit.SECONDS))
            .setResponseTimeout(Timeout.of(30, TimeUnit.SECONDS))
            .build();
        
        // 创建HttpClient,启用代理路由和主动认证
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setRoutePlanner(new DefaultProxyRoutePlanner(proxy))
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            
            HttpGet request = new HttpGet(url);
            
            // 增强Headers模拟真实浏览器
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
            request.setHeader("Accept-Language", "en-US,en;q=0.9");
            request.setHeader("Accept-Encoding", "gzip, deflate, br");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("Upgrade-Insecure-Requests", "1");
            request.setHeader("Sec-Fetch-Dest", "document");
            request.setHeader("Sec-Fetch-Mode", "navigate");
            request.setHeader("Sec-Fetch-Site", "none");
            request.setHeader("Sec-Fetch-User", "?1");
            request.setHeader("Cache-Control", "max-age=0");
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                
                if (statusCode != 200) {
                    throw new RuntimeException("HTTP " + statusCode + " for " + url + 
                        " | 代理: " + proxyHost + ":" + proxyPort + 
                        " | 用户: " + provider.getUsername());
                }
                
                String html = EntityUtils.toString(response.getEntity());
                log.debug("[HttpClient] 抓取成功，HTML长度: {}", html.length());
                return html;
                
            } catch (Exception e) {
                throw new RuntimeException("代理请求失败: " + e.getMessage() + 
                    " | 代理: " + proxyHost + ":" + proxyPort + 
                    " | 用户: " + provider.getUsername(), e);
            }
        }
    }

    private String extractHost(String url) {
        if (url == null) return "localhost";
        
        String[] parts = url.split(":");
        if (parts.length == 2) {
            return parts[0];
        } else if (parts.length >= 3) {
            return parts[1].replace("//", "");
        }
        return url;
    }
    
    private int extractPort(String url) {
        if (url == null) return 8080;
        
        String[] parts = url.split(":");
        if (parts.length == 2) {
            return Integer.parseInt(parts[1]);
        } else if (parts.length >= 3) {
            return Integer.parseInt(parts[2]);
        }
        return 8080;
    }
}
