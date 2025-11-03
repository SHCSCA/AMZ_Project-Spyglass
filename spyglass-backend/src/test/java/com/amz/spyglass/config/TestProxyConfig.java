package com.amz.spyglass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * 测试环境代理配置占位：避免真实网络依赖导致 Bean 注入失败。
 */
@Configuration
@Profile("test")
public class TestProxyConfig {

    @Bean
    public ProxyConfig proxyConfig() {
        ProxyConfig cfg = new ProxyConfig();
        cfg.setEnabled(false); // 关闭代理使用
        cfg.setProviders(List.of()); // 空列表
        return cfg;
    }
}