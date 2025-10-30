package com.amz.spyglass.repository;

import com.amz.spyglass.model.ScrapeTaskModel;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 抓取任务数据访问层
 * 提供对抓取任务执行记录的 CRUD 操作
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-30
 */
public interface ScrapeTaskRepository extends JpaRepository<ScrapeTaskModel, Long> {
}
