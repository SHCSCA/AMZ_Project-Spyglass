package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinKeywords;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ASIN 关键词数据的 Spring Data JPA Repository。
 * <p>
 * V2.1 F-BIZ-001: 为 AsinKeywords 实体提供数据库访问接口。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@Repository
public interface AsinKeywordsRepository extends JpaRepository<AsinKeywords, Long> {

    /**
     * 根据 ASIN 查找其所有关联的关键词。
     *
     * @param asin 亚马逊标准识别码
     * @return 该 ASIN 的关键词列表
     */
    List<AsinKeywords> findByAsin(String asin);

    /**
     * 查找所有需要被追踪的关键词。
     *
     * @return 所有 isTracked 标记为 true 的关键词列表
     */
    List<AsinKeywords> findByIsTrackedTrue();
}
