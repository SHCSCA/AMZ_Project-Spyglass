package com.amz.spyglass.scheduler;

import com.amz.spyglass.config.ScraperProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * HTML dump 清理定时任务：定期删除超过保留天数的 dump 文件，防止日志目录膨胀。
 */
@Component
public class HtmlDumpCleaner {

    private final Logger log = LoggerFactory.getLogger(HtmlDumpCleaner.class);
    private final ScraperProperties props;

    public HtmlDumpCleaner(ScraperProperties props) { this.props = props; }

    @Scheduled(cron = "0 0 3 * * *") // 每天凌晨 3 点执行
    public void clean() {
        if (!props.isHtmlDumpEnabled()) return; // 未启用 dump 功能无需清理
        Path dir = Path.of(props.getHtmlDumpDir());
        if (!Files.exists(dir)) return;
        long retentionDays = props.getHtmlDumpRetentionDays();
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.html")) {
            int deleted = 0;
            for (Path p : stream) {
                try {
                    Instant lastModified = Files.getLastModifiedTime(p).toInstant();
                    if (lastModified.isBefore(cutoff)) {
                        Files.deleteIfExists(p);
                        deleted++;
                    }
                } catch (Exception e) {
                    log.warn("[HtmlDumpCleaner] 删除文件失败 {} msg={}", p, e.getMessage());
                }
            }
            if (deleted > 0) {
                log.info("[HtmlDumpCleaner] 已清理过期 dump 文件数量={} retentionDays={}", deleted, retentionDays);
            } else {
                log.debug("[HtmlDumpCleaner] 无需清理，目录内无过期文件 retentionDays={}", retentionDays);
            }
        } catch (IOException e) {
            log.warn("[HtmlDumpCleaner] 打开目录失败 {} msg={}", dir, e.getMessage());
        }
    }
}

