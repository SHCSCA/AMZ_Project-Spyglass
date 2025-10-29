package com.amz.spyglass.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代理配置（中文注释）
 * 说明：在 application.yml 中以 `proxy.host` / `proxy.port` 等字段配置本模块的代理行为。
 * 示例：
 * proxy.enabled=true
 * proxy.host=127.0.0.1
 * proxy.port=8080
 * proxy.username=xxx (可选)
 * proxy.password=yyy (可选)
 */
@Component
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    private boolean enabled = false;
    private String host;
    private Integer port;
    private String username;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
