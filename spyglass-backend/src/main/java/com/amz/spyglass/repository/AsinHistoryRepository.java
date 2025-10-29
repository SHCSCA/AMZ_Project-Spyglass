package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AsinHistory 仓库（中文注释）
 * 提供按 ASIN 查询历史快照的基本方法。
 */
public interface AsinHistoryRepository extends JpaRepository<AsinHistory, Long> {
    List<AsinHistory> findByAsinIdOrderBySnapshotAtDesc(Long asinId);
}
