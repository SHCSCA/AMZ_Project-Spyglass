package com.amz.spyglass.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ASIN 历史快照响应 DTO
 * 用于返回给前端的历史快照数据，包括价格、BSR、库存等监控指标
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Data
@Schema(description = "ASIN 历史快照响应 DTO")
public class AsinHistoryResponse {

    /**
     * 快照记录ID
     */
    @Schema(description = "快照主键 ID")
    private Long id;

    /**
     * 关联的ASIN记录ID
     */
    @Schema(description = "关联的 ASIN 主键 ID")
    private Long asinId;

    /**
     * 商品标题
     */
    @Schema(description = "商品标题", nullable = true)
    private String title;

    /**
     * Buybox价格
     */
    @Schema(description = "Buybox 价格", nullable = true)
    private BigDecimal price;

    /**
     * Best Seller Rank排名
     */
    @Schema(description = "BSR 主排名", nullable = true)
    private Integer bsr;

    /**
     * BSR主分类
     */
    @Schema(description = "BSR 主分类", nullable = true)
    private String bsrCategory;

    /**
     * BSR子分类
     */
    @Schema(description = "BSR 子分类", nullable = true)
    private String bsrSubcategory;

    /**
     * BSR子分类排名
     */
    @Schema(description = "BSR 子分类排名", nullable = true)
    private Integer bsrSubcategoryRank;

    /**
     * 预估库存数量
     */
    @Schema(description = "估算库存", nullable = true)
    private Integer inventory;

    /**
     * 产品特点（bullet points）
     */
    @Schema(description = "要点（拼接文本）", nullable = true)
    private String bulletPoints;

    /**
     * 主图MD5哈希值，用于检测图片变化
     */
    @Schema(description = "主图 MD5", nullable = true)
    private String imageMd5;

    /**
     * A+页面内容MD5哈希值，用于检测内容变化
     */
    @Schema(description = "A+ 内容 MD5", nullable = true)
    private String aplusMd5;

    /**
     * 总评论数
     */
    @Schema(description = "总评论数", nullable = true)
    private Integer totalReviews;

    /**
     * 平均评分
     */
    @Schema(description = "平均评分", nullable = true)
    private BigDecimal avgRating;

    /**
     * 快照抓取时间
     */
    @Schema(description = "快照时间 (UTC ISO-8601)")
    private Instant snapshotAt;

    /**
     * 优惠券面额，例如 "$10 off" 或 "5%"。
     */
    @Schema(description = "优惠券面额，例如 '$10 off' 或 '5%'", nullable = true)
    private String couponValue;

    /**
     * 是否正在进行秒杀活动。
     */
    @Schema(description = "是否处于秒杀活动", nullable = true)
    private Boolean isLightningDeal;
}
