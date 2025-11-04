package com.amz.spyglass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@Slf4j
public class WebConfig {

    /**
     * 通过环境变量或 application.yml 注入前端允许的跨域来源列表。
     * 使用逗号分隔，示例：
     * FRONTEND_ORIGINS=http://shcamz.xyz:8082,http://156.238.230.229:8082,http://localhost:5173
     * 默认包含常见本地调试与当前生产域名。
     */
    @Value("${frontend.origins:http://shcamz.xyz:8082,http://156.238.230.229:8082,http://localhost:8082,http://localhost:5173}")
    private String frontendOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
        Set<String> origins = new LinkedHashSet<>();
        Arrays.stream(frontendOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(origins::add);

        log.info("[CORS] 注册允许的前端来源: {}", origins);

        registry.addMapping("/api/**")
            // 使用 allowedOriginPatterns 提升灵活性（未来可添加通配符），当前仍然是精确匹配
            .allowedOriginPatterns(origins.toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin")
            .allowCredentials(true)
            .maxAge(3600);
            }
        };
    }
}
