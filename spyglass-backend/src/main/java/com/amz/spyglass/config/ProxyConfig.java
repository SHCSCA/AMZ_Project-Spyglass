package com.amz.spyglass.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "scraper.proxy")
public class ProxyConfig {
    
    private boolean enabled = true;
    private List<ProxyProvider> providers = new ArrayList<>();
    private int rotationInterval = 60;  // 秒
    private int retryCount = 3;
    
    @Data
    public static class ProxyProvider {
        private String name;
        private ProxyType type;
        private String url;
        private String username;
        private String password;
        private boolean active = true;
    }
    
    public enum ProxyType {
        RESIDENTIAL,    // 住宅代理（高质量）
        DATACENTER     // 数据中心代理（备用）
    }
}