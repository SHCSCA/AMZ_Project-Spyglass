package com.amz.spyglass.scraper;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 亚马逊商品页面快照数据传输对象
 * 用于在爬虫层与业务层之间传递已解析的页面数据
 * 包含：标题、价格、BSR、库存、图片MD5、A+内容MD5等核心监控指标
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-30
 */
@Data
public class AsinSnapshotDTO {

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
     * 预估库存数量
     */
    private Integer inventory;

    /**
     * 商品主图的MD5哈希值
     */
    private String imageMd5;

    /**
     * A+内容页面的MD5哈希值
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
     * 抓取时间戳
     */
    private Instant snapshotAt = Instant.now();
}
