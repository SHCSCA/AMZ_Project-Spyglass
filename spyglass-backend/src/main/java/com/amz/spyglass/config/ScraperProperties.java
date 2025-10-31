package com.amz.spyglass.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 抓取相关配置（中文注释）
 * 包含是否启用图片二进制下载以计算真实 MD5 以及下载超时等参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "scraper")
public class ScraperProperties {

    /** 是否下载图片二进制并计算 MD5（默认 false，以避免测试/CI 中的外网依赖） */
    private boolean downloadImageBinary = false;

    /** 图片下载超时时间（毫秒） */
    private int imageDownloadTimeoutMs = 5000;

    /** 是否启用原始 HTML dump（在关键字段缺失或命中防爬时保存） */
    private boolean htmlDumpEnabled = false;
    /** HTML dump 保存目录（相对工作目录） */
    private String htmlDumpDir = "logs/html-dump";
    /** Jsoup 抓取前随机延迟最小毫秒 */
    private int randomDelayMinMs = 150;
    /** Jsoup 抓取前随机延迟最大毫秒 */
    private int randomDelayMaxMs = 500;
    /** 当关键字段缺失时的最大重试次数（含首次） */
    private int maxRetry = 3;

    /** HTML dump 保留天数 */
    private int htmlDumpRetentionDays = 3;
}
