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

    @Column(nullable = false, length = 20)
    private String asin;

    @Column(nullable = false, length = 255)
    private String keyword;

    @Column(nullable = false)
    private Boolean isTracked = true;
}
