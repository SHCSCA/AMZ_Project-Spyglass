package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ProxyManagerTest {

    private ProxyConfig proxyConfig;
    private ProxyManager proxyManager;

    @BeforeEach
    void setUp() {
        proxyConfig = new ProxyConfig();
        
        // 配置测试用的代理
        ProxyConfig.ProxyProvider provider1 = new ProxyConfig.ProxyProvider();
        provider1.setName("test-proxy-1");
        provider1.setType(ProxyConfig.ProxyType.RESIDENTIAL);
        provider1.setUrl("http://proxy1.test:8080");
        provider1.setUsername("user1");
        provider1.setPassword("pass1");
        
        ProxyConfig.ProxyProvider provider2 = new ProxyConfig.ProxyProvider();
        provider2.setName("test-proxy-2");
        provider2.setType(ProxyConfig.ProxyType.DATACENTER);
        provider2.setUrl("http://proxy2.test:8080");
        
        proxyConfig.setProviders(Arrays.asList(provider1, provider2));
        proxyManager = new ProxyManager(proxyConfig);
    }

    @Test
    void shouldRotateProxies() {
        ProxyConfig.ProxyProvider first = proxyManager.nextProxy();
        ProxyConfig.ProxyProvider second = proxyManager.nextProxy();
        ProxyConfig.ProxyProvider third = proxyManager.nextProxy();
        
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getName(), "test-proxy-1");
        assertEquals(second.getName(), "test-proxy-2");
        assertEquals(third.getName(), "test-proxy-1");  // 应该循环回到第一个
    }

    @Test
    void shouldCreateProxiedRestTemplate() {
        ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
        RestTemplate template = proxyManager.createProxiedRestTemplate(provider);
        
        assertNotNull(template);
        assertTrue(template.getInterceptors().size() > 0);  // 应该有认证拦截器
    }
    
    @Test
    void shouldHandleDisabledProxy() {
        proxyConfig.setEnabled(false);
        ProxyConfig.ProxyProvider provider = proxyManager.nextProxy();
        
        assertNull(provider);  // 禁用代理时应返回null
    }
}