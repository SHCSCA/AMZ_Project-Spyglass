package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * V2.1 F-BIZ-001: ASIN 关键词实体。
 */
@Getter
@Setter
@Entity
@Table(name = "asin_keywords")
public class AsinKeywords extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asin_id", nullable = false, foreignKey = @ForeignKey(name = "fk_asin_keywords_asin"))
    private AsinModel asin;

    @Column(nullable = false, length = 255)
    private String keyword;

    @Column(name = "is_tracked", nullable = false)
    private Boolean isTracked = Boolean.TRUE;
}
