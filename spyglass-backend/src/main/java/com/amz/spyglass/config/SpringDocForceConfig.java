package com.amz.spyglass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;
import lombok.extern.slf4j.Slf4j;

/**
 * SpringDocForceConfig
 * 生产环境 /v3/api-docs 未注册时，通过显式声明 GroupedOpenApi Bean 触发 springdoc 自动配置。
 * 某些环境下仅有 OpenAPI Bean 不足以加载控制器扫描，这里强制扫描 controller 包。
 */
@Configuration
@Slf4j
public class SpringDocForceConfig {

    @Bean
    public GroupedOpenApi defaultPublicApi() {
        log.info("[SpringDocForce] Register GroupedOpenApi for /api/** paths");
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/**")
                .build();
    }
}
