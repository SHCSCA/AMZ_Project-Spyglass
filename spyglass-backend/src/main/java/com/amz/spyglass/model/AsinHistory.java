package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * ASIN 历史快照实体
 * 用于保存每次抓取的快照数据，包括价格、BSR、库存、图片MD5、A+内容MD5等
 * 
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Getter
@Setter
@NoArgsConstructor
@Deprecated
// 旧的历史快照实体，已迁移到 AsinHistoryModel.java。保留以便回溯。
class AsinHistoryLegacy extends BaseEntityModel {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的 ASIN 实体
     */
    // 旧关联引用，指向 AsinLegacy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asin_id", nullable = false)
    private AsinLegacy asin;

    /**
     * 商品标题
     */
    @Column(columnDefinition = "TEXT")
    private String title;

    /**
     * 当前价格（Buybox价格）
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    /**
     * Best Seller Rank排名
     */
    private Integer bsr;

    /**
     * 预估库存数量
     */
    private Integer inventory;

    /**
     * 主图MD5哈希值
     */
    @Column(length = 64)
    private String imageMd5;

    /**
     * A+页面内容MD5哈希值
     */
    @Column(length = 64)
    private String aplusMd5;

    /**
     * 总评论数
     */
    private Integer totalReviews;

    /**
     * 平均评分
     */
    @Column(precision = 2, scale = 1)
    private BigDecimal avgRating;

    /**
     * 快照时间
     */
    @Column(nullable = false)
    private Instant snapshotAt;
}
