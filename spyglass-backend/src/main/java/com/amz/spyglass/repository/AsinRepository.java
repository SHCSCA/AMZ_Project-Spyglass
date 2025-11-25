package com.amz.spyglass.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import com.amz.spyglass.model.AsinModel;

/**
 * ASIN 数据访问层
 * 提供对 ASIN 监控目标的 CRUD 操作，额外支持按 ASIN 编码查询的方法
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-30
 */
@Repository
public interface AsinRepository extends JpaRepository<AsinModel, Long>, JpaSpecificationExecutor<AsinModel> {
    /**
     * [V2.1 优化] 使用 EntityGraph 解决 LazyInitializationException
     *
     * @param spec 查询条件
     * @param pageable 分页参数
     * @return 分页的 ASIN 数据
     *
     * PRD F-STABLE-003: ASIN 列表接口偶发 LazyInitializationException。
     * 原因：在获取 AsinModel 列表后，序列化为 JSON 时，由于 Hibernate Session 已关闭，
     *      访问懒加载的 group 属性会抛出异常。
     * 解决方案：使用 @EntityGraph 注解，强制 Hibernate 在查询 AsinModel 时，
     *         通过 LEFT JOIN 一次性加载 attributePaths 中指定的关联属性（如此处的 "group"）。
     *         这被称为 "FetchType.EAGER" 的运行时覆盖，避免了 N+1 查询，也根治了懒加载异常。
     */
    @Override
    @EntityGraph(attributePaths = {"group"})
    @NonNull
    Page<AsinModel> findAll(@Nullable Specification<AsinModel> spec, @NonNull Pageable pageable);

    /**
     * [V2.1 优化] 同样为 findAll() 方法应用 EntityGraph
     * 确保在不带任何查询条件直接获取所有 ASIN 列表时，也能预加载 group 信息。
     */
    @Override
    @EntityGraph(attributePaths = {"group"})
    @NonNull
    List<AsinModel> findAll();

    /**
     * 根据 ASIN 字符串查找实体
     * @param asin ASIN 字符串
     * @return Optional<AsinModel>
     */
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
