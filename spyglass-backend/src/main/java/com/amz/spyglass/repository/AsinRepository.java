package com.amz.spyglass.repository;

import com.amz.spyglass.model.Asin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 数据库访问：AsinRepository（中文注释）
 * 说明：继承 JpaRepository 提供标准的 CRUD 操作，额外提供按 asin 字段查询的方法。
 */
public interface AsinRepository extends JpaRepository<Asin, Long> {
    Optional<Asin> findByAsin(String asin);
}
