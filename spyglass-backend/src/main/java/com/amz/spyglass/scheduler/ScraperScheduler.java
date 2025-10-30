package com.amz.spyglass.scheduler;

import com.amz.spyglass.model.Asin;
import com.amz.spyglass.model.ScrapeTask;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.repository.ScrapeTaskRepository;
import com.amz.spyglass.service.ScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.Instant;

@Component
@Profile("!test")
public class ScraperScheduler {

    private final Logger logger = LoggerFactory.getLogger(ScraperScheduler.class);
    private final AsinRepository asinRepository;
    private final ScraperService scraperService;
    private final ScrapeTaskRepository scrapeTaskRepository;
    private final com.amz.spyglass.repository.AsinHistoryRepository asinHistoryRepository;

    public ScraperScheduler(AsinRepository asinRepository, ScraperService scraperService, ScrapeTaskRepository scrapeTaskRepository, com.amz.spyglass.repository.AsinHistoryRepository asinHistoryRepository) {
        this.asinRepository = asinRepository;
        this.scraperService = scraperService;
        this.scrapeTaskRepository = scrapeTaskRepository;
        this.asinHistoryRepository = asinHistoryRepository;
    }

    // default every 4 hours (14400000 ms). Can be overridden with property scraper.fixedDelayMs
    @Scheduled(fixedDelayString = "${scraper.fixedDelayMs:14400000}")
    public void runAll() {
        for (Asin a : asinRepository.findAll()) {
            runForAsinAsync(a.getId());
        }
    }

    @Async
    public void runForAsinAsync(Long asinId) {
        ScrapeTask task = new ScrapeTask();
        task.setAsinId(asinId);
        task.setStatus(ScrapeTask.TaskStatus.RUNNING);
        task.setRunAt(Instant.now());
        scrapeTaskRepository.save(task);

        try {
            // placeholder: use scraperService to fetch title (real logic should fetch price, bsr, etc.)
            Asin a = asinRepository.findById(asinId).orElseThrow();
            // 调用 ScraperService 获取完整的页面快照（包含 price/bsr/inventory/imageMd5/aplusMd5）
            com.amz.spyglass.scraper.AsinSnapshot snap = scraperService.fetchSnapshot("https://www.amazon.com/dp/" + a.getAsin());

            task.setStatus(ScrapeTask.TaskStatus.SUCCESS);
            task.setMessage("title=" + (snap.getTitle() == null ? "" : snap.getTitle()));
            task.setRunAt(Instant.now());
            scrapeTaskRepository.save(task);

            // 保存历史快照到 AsinHistory 实体
            try {
                com.amz.spyglass.model.AsinHistory h = new com.amz.spyglass.model.AsinHistory();
                h.setAsin(a);
                h.setTitle(snap.getTitle());
                h.setPrice(snap.getPrice());
                h.setBsr(snap.getBsr());
                h.setInventory(snap.getInventory());
                h.setImageMd5(snap.getImageMd5());
                h.setAplusMd5(snap.getAplusMd5());
                h.setSnapshotAt(snap.getSnapshotAt() == null ? Instant.now() : snap.getSnapshotAt());
                asinHistoryRepository.save(h);
            } catch (Exception e) {
                logger.warn("failed to save asin history for {}: {}", asinId, e.getMessage());
            }
        } catch (Exception ex) {
            logger.error("scrape failed for asin {}", asinId, ex);
            task.setStatus(ScrapeTask.TaskStatus.FAILED);
            task.setMessage(ex.getMessage());
            task.setRunAt(Instant.now());
            scrapeTaskRepository.save(task);
        }
    }
}
