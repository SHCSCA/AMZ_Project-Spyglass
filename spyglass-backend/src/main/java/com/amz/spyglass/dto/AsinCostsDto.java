package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * V2.1 F-BIZ-002: ASIN 成本配置的数据传输对象 (DTO)。
 * 用于在 API 层传输 ASIN 的各项成本数据。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@Schema(description = "ASIN 成本配置的数据传输对象")
public class AsinCostsDto {

    @Schema(description = "亚马逊标准识别码", example = "B08N5WRWNW", accessMode = Schema.AccessMode.READ_ONLY)
    private String asin;

    @NotNull
    @PositiveOrZero
    @Schema(description = "采购成本", example = "15.50")
    private BigDecimal purchaseCost;

    @PositiveOrZero
    @Schema(description = "头程运费", example = "3.20")
    private BigDecimal shippingCost;

    @PositiveOrZero
    @Schema(description = "FBA派送费", example = "5.80")
    private BigDecimal fbaFee;

    @PositiveOrZero
    @Schema(description = "关税税率（例如 0.06 表示 6%）", example = "0.06")
    private BigDecimal tariffRate;

    @PositiveOrZero
    @Schema(description = "其他杂项成本", example = "0.50")
    private BigDecimal otherCost;

    // Getters and Setters
    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public BigDecimal getFbaFee() {
        return fbaFee;
    }

    public void setFbaFee(BigDecimal fbaFee) {
        this.fbaFee = fbaFee;
    }

    public BigDecimal getTariffRate() {
        return tariffRate;
    }

    public void setTariffRate(BigDecimal tariffRate) {
        this.tariffRate = tariffRate;
    }

    public BigDecimal getOtherCost() {
        return otherCost;
    }

    public void setOtherCost(BigDecimal otherCost) {
        this.otherCost = otherCost;
    }
}
