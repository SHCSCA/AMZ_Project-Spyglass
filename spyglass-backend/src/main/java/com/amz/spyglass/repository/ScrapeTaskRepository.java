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
	/**
	 * 根据 ASIN ID 查询最新一条任务记录
	 * 基于创建时间倒序排序
	 * @param asinId ASIN ID
	 * @return 最新的任务记录，如果不存在则返回 null
	 */
	ScrapeTaskModel findFirstByAsinIdOrderByCreatedAtDesc(Long asinId);

}
