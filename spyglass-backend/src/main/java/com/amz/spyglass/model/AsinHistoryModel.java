package com.amz.spyglass.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * ASIN 历史快照实体
 * 用于保存每次抓取的快照数据，包括价格、BSR、库存、图片MD5、A+内容MD5等
 * 
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "asin_history")
public class AsinHistoryModel extends BaseEntityModel {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的 ASIN 实体
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asin_id", nullable = false)
    private AsinModel asin;

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
     * BSR大类名称
     */
    @Column(length = 255)
    private String bsrCategory;

    /**
     * BSR小类名称
     */
    @Column(length = 255)
    private String bsrSubcategory;

    /**
     * BSR小类排名
     */
    private Integer bsrSubcategoryRank;

    /**
     * 预估库存数量
     */
    private Integer inventory;

    /**
     * 是否命中限购（999 大法返回限购阈值时为 true）
     */
    @Column(name = "inventory_limited")
    private Boolean inventoryLimited;

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
     * 最新差评的 MD5 哈希值（基于评论内容+时间计算，用于检测新差评）
     */
    @Column(length = 64)
    private String latestNegativeReviewMd5;

    /**
     * 商品五点要点（feature bullets），以多行文本存储，每个要点一行。
     * 注意：为避免复杂的 JSON/结构化存储，这里使用 TEXT 字段；如果需要更复杂的查询可改为 JSON 字段。
     */
    @Column(columnDefinition = "TEXT")
    private String bulletPoints;

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

    /**
     * V2.1 F-DATA-002: 优惠券面额(例如 "$10 off" 或 "5%")
     */
    @Column(name = "coupon_value", length = 64)
    private String couponValue;

    /**
     * V2.1 F-DATA-002: 是否正在进行秒杀活动(1=是, 0=否)
     */
    @Column(name = "is_lightning_deal")
    private Boolean isLightningDeal = false;
}
