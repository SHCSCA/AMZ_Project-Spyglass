package com.amz.spyglass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类（中文注释）：
 * 1) @SpringBootApplication：Spring Boot 应用入口
 * 2) @EnableScheduling：启用定时任务（scheduler）用于抓取调度
 * 3) @EnableAsync：启用异步执行，以便对单个 ASIN 的抓取异步化，避免阻塞
 *
 * 注意：在单元测试中，我们通过 profile 或 test 属性禁用定时任务，避免在测试期间发起网络请求。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@Profile("!test") // test 环境下不加载此主配置类的调度/异步特性
public class SpyglassApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpyglassApplication.class, args);
    }
}
