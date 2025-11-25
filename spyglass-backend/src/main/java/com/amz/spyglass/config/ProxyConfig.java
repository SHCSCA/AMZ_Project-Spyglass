package com.amz.spyglass.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * V2.1 升级：代理配置类 (F-STABLE-001)
 *
 * 变更：
 * 1. 不再使用简单的 String 列表，而是定义了一个内部静态类 `ProxyEntry` 来结构化地存储每个代理的 host, port, username, password。
 * 2. 这种结构使得配置更清晰，并为 ProxyManager 创建 ProxyInstance 提供了完整的原始数据。
 */
@Configuration
@ConfigurationProperties(prefix = "scraper.proxy")
@Validated
@Data
public class ProxyConfig {

    private boolean enabled = false;

    /**
     * 连续失败阈值，达到后将触发熔断。
     */
    private int failureThreshold = 5;

    /**
     * 熔断持续秒数，默认 10 分钟。
     */
    private long cooldownSeconds = 600L;

    private List<ProxyEntry> list = new ArrayList<>();

    /**
     * 内部静态类，用于映射 application.yml 中的每个代理条目
     */
    @Data
    public static class ProxyEntry {
        /** 为代理命名，便于日志追踪，可选 */
        private String name;

        @NotBlank
        private String host;

        private int port;

        private String username;

        private String password;

        public String displayName() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            return host + ":" + port;
        }
    }
}