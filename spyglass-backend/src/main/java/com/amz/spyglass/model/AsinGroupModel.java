package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * ASIN 分组实体：用于将多个竞争 ASIN 归为同一“我的产品”对应的竞品集合。
 * 典型场景：一个自有产品对应市场上多个不同品牌/变体的竞品，需要统一查看与统计。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "asin_group")
public class AsinGroupModel extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 分组名称（如："人体工学椅竞品"、"50寸L桌竞品"）
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * 备注或描述，可选。
     */
    @Column(length = 512)
    private String description;

    /**
     * 组内 ASIN 数（冗余字段，查询统计时可按需维护；当前初始化为0，不在本迭代更新增减）
     */
    @Column(name = "asin_count")
    private Integer asinCount = 0;

    @PrePersist
    public void prePersist() {
        setCreatedAt(Instant.now());
        setUpdatedAt(Instant.now());
    }

    @PreUpdate
    public void preUpdate() {
        setUpdatedAt(Instant.now());
    }
}
