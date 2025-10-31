package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ProxyManager {

    private final ProxyConfig proxyConfig;
    private final List<ProxyConfig.ProxyProvider> providers;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    @Autowired
    public ProxyManager(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        this.providers = proxyConfig.getProviders();
    }
    
    /**
     * 获取下一个可用的代理配置
     */
    public ProxyConfig.ProxyProvider nextProxy() {
        if (!proxyConfig.isEnabled() || providers.isEmpty()) {
            return null;
        }
        
        int index = currentIndex.getAndIncrement() % providers.size();
        return providers.get(index);
    }
    
    /**
     * 为 RestTemplate 配置代理
     */
    public RestTemplate createProxiedRestTemplate(ProxyConfig.ProxyProvider provider) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 解析代理 URL 并配置
        String proxyUrl = provider.getUrl();
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
        
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        factory.setProxy(proxy);
        
        RestTemplate template = new RestTemplate(factory);
        
        // 如果有认证信息，添加代理认证头
        if (provider.getUsername() != null && provider.getPassword() != null) {
            String auth = provider.getUsername() + ":" + provider.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            
            template.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add(HttpHeaders.PROXY_AUTHORIZATION,
                    "Basic " + encodedAuth);
                return execution.execute(request, body);
            });
        }
        
        return template;
    }
    
    /**
     * 标记代理失败并切换到下一个
     */
    public void markProxyFailure(ProxyConfig.ProxyProvider provider) {
        log.warn("代理失败: {}", provider.getName());
        // TODO: 实现代理健康检查与自动切换逻辑
    }
}