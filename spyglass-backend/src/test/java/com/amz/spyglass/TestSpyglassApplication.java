package com.amz.spyglass;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试环境专用启动类：不启用调度与异步，保证集成测试纯粹执行业务逻辑。
 */
@SpringBootApplication
public class TestSpyglassApplication { }
