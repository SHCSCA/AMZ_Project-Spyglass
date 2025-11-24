package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinCosts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ASIN 成本数据的 Spring Data JPA Repository。
 * <p>
 * V2.1 F-BIZ-002: 为 AsinCosts 实体提供数据库访问接口。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@Repository
public interface AsinCostsRepository extends JpaRepository<AsinCosts, Long> {

    /**
     * 根据 ASIN 查找其成本配置。
     *
     * @param asin 亚马逊标准识别码
     * @return 包含成本配置的 Optional，如果不存在则为空
     */
    Optional<AsinCosts> findByAsin_Id(Long asinId);

    boolean existsByAsin_Id(Long asinId);
}
