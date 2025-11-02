package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * ASIN 数据访问层
 * 提供对 ASIN 监控目标的 CRUD 操作，额外支持按 ASIN 编码查询的方法
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-30
 */
public interface AsinRepository extends JpaRepository<AsinModel, Long> {
    Optional<AsinModel> findByAsin(String asin);
    Optional<AsinModel> findByAsinAndSite(String asin, String site);
    Page<AsinModel> findAllByGroupId(Long groupId, Pageable pageable);
}
