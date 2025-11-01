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
        private String type; // 原枚举改为字符串
        private String url;
        private String username;
        private String password;
        private boolean active = true;
    }
}