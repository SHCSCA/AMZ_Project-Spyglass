package com.amz.spyglass.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置（中文注释）
 * 说明：通过此配置类可以设置生成的 OpenAPI 元数据，springdoc-openapi 会自动暴露 /v3/api-docs 和 Swagger UI。
 * 如需更多定制（服务器列表、全局 securitySchemes 等），可在此继续扩展。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI spyglassOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spyglass API")
                        .version("0.0.1")
                        .description("Spyglass 后端 API：ASIN 监控与抓取接口（自动生成的 OpenAPI）"));
    }
}
