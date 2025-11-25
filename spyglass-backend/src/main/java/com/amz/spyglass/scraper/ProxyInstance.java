package com.amz.spyglass.scraper;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 代理运行时实例，负责记录失败次数和熔断状态。
 */
public class ProxyInstance {

    private final String id;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant circuitBreakerUntil = Instant.EPOCH;

    public ProxyInstance(String id, String host, int port, String username, String password) {
        this.id = Objects.requireNonNull(id, "Proxy id must not be null");
        this.host = Objects.requireNonNull(host, "Proxy host must not be null");
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    public Optional<String> getPassword() {
        return Optional.ofNullable(password);
    }

    public boolean isAuthenticationRequired() {
        return username != null && password != null;
    }

    public String buildProxyHeaderValue() {
        if (!isAuthenticationRequired()) {
            return null;
        }
        String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes());
    }

    public boolean isAvailable() {
        return Instant.now().isAfter(circuitBreakerUntil);
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        circuitBreakerUntil = Instant.EPOCH;
    }

    public void recordFailure(int failureThreshold, Duration cooldown) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            circuitBreakerUntil = Instant.now().plus(cooldown);
        }
    }

    @Override
    public String toString() {
        return id + "(" + host + ':' + port + ")";
    }
}
