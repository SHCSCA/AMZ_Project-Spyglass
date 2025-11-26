package com.amz.spyglass.scraper;

import com.amz.spyglass.config.ProxyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ProxyManager {

    private final boolean enabled;
    private final int failureThreshold;
    private final Duration cooldown;
    private final List<ProxyInstance> proxies;
    private final AtomicInteger cursor = new AtomicInteger();

    public ProxyManager(ProxyConfig proxyConfig) {
        this.enabled = proxyConfig.isEnabled();
        this.failureThreshold = Math.max(1, proxyConfig.getFailureThreshold());
        this.cooldown = Duration.ofSeconds(Math.max(1L, proxyConfig.getCooldownSeconds()));

        if (!enabled) {
            this.proxies = Collections.emptyList();
            return;
        }

        this.proxies = proxyConfig.getList().stream()
                .map(entry -> new ProxyInstance(entry.displayName(), entry.getHost(), entry.getPort(), entry.getUsername(), entry.getPassword()))
                .toList();

        if (proxies.isEmpty()) {
            log.warn("代理功能已启用，但未提供任何代理条目。");
        }
    }

    public Optional<ProxyInstance> borrow() {
        if (!enabled || proxies.isEmpty()) {
            return Optional.empty();
        }

        int attempts = proxies.size();
        for (int i = 0; i < attempts; i++) {
            int index = Math.floorMod(cursor.getAndIncrement(), proxies.size());
            ProxyInstance candidate = proxies.get(index);
            if (candidate.isAvailable()) {
                return Optional.of(candidate);
            }
        }

        // 所有代理都处于熔断状态
        return Optional.empty();
    }

    public void recordSuccess(ProxyInstance proxyInstance) {
        if (proxyInstance != null) {
            proxyInstance.recordSuccess();
        }
    }

    public void recordFailure(ProxyInstance proxyInstance) {
        if (proxyInstance != null) {
            proxyInstance.recordFailure(failureThreshold, cooldown);
        }
    }

    public RestTemplate createProxiedRestTemplate(ProxyInstance proxyInstance) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyInstance.getHost(), proxyInstance.getPort()));
        factory.setProxy(proxy);

        RestTemplate template = new RestTemplate(factory);
        String header = proxyInstance.buildProxyHeaderValue();
        if (header != null) {
            template.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add(HttpHeaders.PROXY_AUTHORIZATION, header);
                return execution.execute(request, body);
            });
        }
        return template;
    }

    public List<ProxyInstance> getProxies() {
        return proxies;
    }

    public Optional<ProxyInstance> findByHostAndPort(String host, int port) {
        if (!enabled || proxies.isEmpty()) {
            return Optional.empty();
        }
        return proxies.stream()
                .filter(p -> p.getHost().equals(host) && p.getPort() == port)
                .findFirst();
    }
}