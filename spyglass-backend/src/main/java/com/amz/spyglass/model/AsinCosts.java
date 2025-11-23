package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * V2.1 F-BIZ-002: ASIN 成本配置实体。
 */
@Getter
@Setter
@Entity
@Table(name = "asin_costs")
public class AsinCosts extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String asin;

    // 采购成本
    @Column(precision = 12, scale = 4)
    private BigDecimal purchaseCost;

    // 头程运费
    @Column(precision = 12, scale = 4)
    private BigDecimal shippingCost;

    // FBA 派送费
    @Column(precision = 12, scale = 4)
    private BigDecimal fbaFee;

    // 关税税率（例如 0.06 表示 6%）
    @Column(precision = 6, scale = 4)
    private BigDecimal tariffRate;

    // 其他杂项费用
    @Column(precision = 12, scale = 4)
    private BigDecimal otherCost;
}
