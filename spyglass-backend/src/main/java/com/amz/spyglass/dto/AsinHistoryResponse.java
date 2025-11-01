package com.amz.spyglass.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/**
 * ASIN 历史快照响应 DTO
 * 用于返回给前端的历史快照数据，包括价格、BSR、库存等监控指标
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
public class AsinHistoryResponse {

    /**
     * 快照记录ID
     */
    private Long id;

    /**
     * 关联的ASIN记录ID
     */
    private Long asinId;

    /**
     * 商品标题
     */
    private String title;

    /**
     * Buybox价格
     */
    private BigDecimal price;

    /**
     * Best Seller Rank排名
     */
    private Integer bsr;

    /**
     * BSR主分类
     */
    private String bsrCategory;

    /**
     * BSR子分类
     */
    private String bsrSubcategory;

    /**
     * BSR子分类排名
     */
    private Integer bsrSubcategoryRank;

    /**
     * 预估库存数量
     */
    private Integer inventory;

    /**
     * 产品特点（bullet points）
     */
    private String bulletPoints;

    /**
     * 主图MD5哈希值，用于检测图片变化
     */
    private String imageMd5;

    /**
     * A+页面内容MD5哈希值，用于检测内容变化
     */
    private String aplusMd5;

    /**
     * 总评论数
     */
    private Integer totalReviews;

    /**
     * 平均评分
     */
    private BigDecimal avgRating;

    /**
     * 快照抓取时间
     */
    private Instant snapshotAt;
}
