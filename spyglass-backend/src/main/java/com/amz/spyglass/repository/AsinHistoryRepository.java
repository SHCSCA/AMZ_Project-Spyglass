package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinHistoryModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * AsinHistory 仓库（中文注释）
 * 提供按 ASIN 查询历史快照的基本方法。
 */
public interface AsinHistoryRepository extends JpaRepository<AsinHistoryModel, Long> {
    List<AsinHistoryModel> findByAsinId(Long asinId);
    List<AsinHistoryModel> findByAsinIdOrderBySnapshotAtDesc(Long asinId);
    List<AsinHistoryModel> findByAsinIdAndSnapshotAtAfterOrderBySnapshotAtDesc(Long asinId, Instant since);
}
