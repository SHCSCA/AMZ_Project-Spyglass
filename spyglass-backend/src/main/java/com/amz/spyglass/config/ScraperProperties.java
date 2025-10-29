package com.amz.spyglass.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 抓取相关配置（中文注释）
 * 包含是否启用图片二进制下载以计算真实 MD5 以及下载超时等参数。
 */
@Component
@ConfigurationProperties(prefix = "scraper")
public class ScraperProperties {

    /** 是否下载图片二进制并计算 MD5（默认 false，以避免测试/CI 中的外网依赖） */
    private boolean downloadImageBinary = false;

    /** 图片下载超时时间（毫秒） */
    private int imageDownloadTimeoutMs = 5000;

    public boolean isDownloadImageBinary() {
        return downloadImageBinary;
    }

    public void setDownloadImageBinary(boolean downloadImageBinary) {
        this.downloadImageBinary = downloadImageBinary;
    }

    public int getImageDownloadTimeoutMs() {
        return imageDownloadTimeoutMs;
    }

    public void setImageDownloadTimeoutMs(int imageDownloadTimeoutMs) {
        this.imageDownloadTimeoutMs = imageDownloadTimeoutMs;
    }
}
