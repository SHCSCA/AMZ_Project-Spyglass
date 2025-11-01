package com.amz.spyglass.model.alert;

import com.amz.spyglass.model.BaseEntityModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_alert")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PriceAlert extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asin_id", nullable = false)
    private Long asinId;

    @Column(name = "old_price", precision = 14, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 14, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "change_percent", precision = 5, scale = 2)
    private BigDecimal changePercent;

    @Column(name = "alert_at", nullable = false)
    private Instant alertAt = Instant.now();

    // ----- 新增的上下文字段：记录价格变化时其它关键字段的旧值与新值 -----
    @Lob
    @Column(name = "old_title")
    private String oldTitle;

    @Lob
    @Column(name = "new_title")
    private String newTitle;

    @Column(name = "old_image_md5", length = 64)
    private String oldImageMd5;

    @Column(name = "new_image_md5", length = 64)
    private String newImageMd5;

    @Lob
    @Column(name = "old_bullet_points")
    private String oldBulletPoints;

    @Lob
    @Column(name = "new_bullet_points")
    private String newBulletPoints;

    @Column(name = "old_aplus_md5", length = 64)
    private String oldAplusMd5;

    @Column(name = "new_aplus_md5", length = 64)
    private String newAplusMd5;

    public PriceAlert(Long asinId, BigDecimal oldPrice, BigDecimal newPrice, BigDecimal changePercent) {
        this.asinId = asinId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.changePercent = changePercent;
    }
}
