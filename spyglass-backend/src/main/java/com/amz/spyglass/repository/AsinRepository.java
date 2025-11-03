package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinModel;
import org.springframework.data.jpa.repository.EntityGraph;
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
    @EntityGraph(attributePaths = {"group"})
    Optional<AsinModel> findByAsin(String asin);

    @EntityGraph(attributePaths = {"group"})
    Optional<AsinModel> findByAsinAndSite(String asin, String site);

    @EntityGraph(attributePaths = {"group"})
    Page<AsinModel> findAllByGroupId(Long groupId, Pageable pageable);

    /**
     * 使用派生查询生成 "select a from AsinModel a" 并通过 @EntityGraph 预加载 group，避免懒加载异常。
     * 命名约定：findAllBy + 空谓词 可以让 Spring Data 生成与 findAll 等价的查询，但可以自定义注解。
     */
    @EntityGraph(attributePaths = {"group"})
    Page<AsinModel> findByIdIsNotNull(Pageable pageable);
}
