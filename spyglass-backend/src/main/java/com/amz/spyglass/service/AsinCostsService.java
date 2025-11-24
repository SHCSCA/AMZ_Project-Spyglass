package com.amz.spyglass.service;

import com.amz.spyglass.dto.AsinCostsDto;
import com.amz.spyglass.model.AsinCosts;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinCostsRepository;
import com.amz.spyglass.repository.AsinRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * V2.1 F-BIZ-002: 管理 ASIN 成本配置的服务。
 * <p>
 * 负责处理 ASIN 成本的增删改查逻辑，并提供利润计算功能。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@Slf4j
@Service
public class AsinCostsService {

    private final AsinCostsRepository asinCostsRepository;
    private final AsinRepository asinRepository;

    @Autowired
    public AsinCostsService(AsinCostsRepository asinCostsRepository, AsinRepository asinRepository) {
        this.asinCostsRepository = asinCostsRepository;
        this.asinRepository = asinRepository;
    }

    /**
     * 创建或更新一个 ASIN 的成本配置。
     * 如果该 ASIN 已有成本配置，则更新；否则创建新的记录。
     *
     * @param asin        亚马逊标准识别码
     * @param costsDto    包含成本信息的 DTO
     * @return 已保存的成本配置 DTO
     */
    @Transactional
    public AsinCostsDto createOrUpdateCosts(String asin, AsinCostsDto costsDto) {
        // 验证 ASIN 是否存在于主表中
        AsinModel asinModel = asinRepository.findByAsin(asin)
            .orElseThrow(() -> new EntityNotFoundException("ASIN not found: " + asin));

        AsinCosts costs = asinCostsRepository.findByAsinId(asinModel.getId())
            .orElseGet(AsinCosts::new);

        costs.setAsin(asinModel);
        costs.setPurchaseCost(costsDto.getPurchaseCost());
        costs.setShippingCost(costsDto.getShippingCost());
        costs.setFbaFee(costsDto.getFbaFee());
        costs.setTariffRate(costsDto.getTariffRate());
        costs.setOtherCost(costsDto.getOtherCost());

        AsinCosts savedCosts = asinCostsRepository.save(costs);
        log.info("成功为 ASIN: {} 创建/更新成本配置。", asin);
        return convertToDto(savedCosts);
    }

    /**
     * 根据 ASIN 获取成本配置。
     *
     * @param asin 亚马逊标准识别码
     * @return Optional 包含成本配置 DTO
     */
    @Transactional(readOnly = true)
    public Optional<AsinCostsDto> getCostsByAsin(String asin) {
        return asinRepository.findByAsin(asin)
            .flatMap(model -> asinCostsRepository.findByAsinId(model.getId()))
            .map(this::convertToDto);
    }

    /**
     * 根据 ASIN 删除成本配置。
     *
     * @param asin 亚马逊标准识别码
     */
    @Transactional
    public void deleteCosts(String asin) {
        AsinModel asinModel = asinRepository.findByAsin(asin)
            .orElseThrow(() -> new EntityNotFoundException("ASIN not found: " + asin));

        AsinCosts costs = asinCostsRepository.findByAsinId(asinModel.getId())
            .orElseThrow(() -> new EntityNotFoundException("No cost configuration found for ASIN: " + asin));
        Long costId = costs.getId();
        asinCostsRepository.deleteById(Objects.requireNonNull(costId, "成本配置缺少主键 ID"));
        log.info("成功删除 ASIN: {} 的成本配置。", asin);
    }

    /**
     * PRD F-BIZ-002: 利润反推计算器核心逻辑。
     * 根据给定的售价和 ASIN，计算其利润和利润率。
     *
     * @param asin  亚马逊标准识别码
     * @param price 当前售价
     * @return 包含利润和利润率的 DTO，如果成本未配置则返回空的 DTO
     */
    public ProfitCalculationDto calculateProfit(String asin, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return new ProfitCalculationDto(null, null, "无效的售价");
        }

        Optional<AsinModel> asinModelOpt = asinRepository.findByAsin(asin);
        if (asinModelOpt.isEmpty()) {
            return new ProfitCalculationDto(null, null, "ASIN 不存在");
        }

        Optional<AsinCosts> costsOpt = asinCostsRepository.findByAsinId(asinModelOpt.get().getId());
        if (costsOpt.isEmpty()) {
            log.warn("ASIN: {} 缺少成本配置，无法计算利润。", asin);
            return new ProfitCalculationDto(null, null, "成本数据未配置");
        }

        AsinCosts costs = costsOpt.get();
        BigDecimal totalCost = BigDecimal.ZERO;

        // 累加各项成本
        totalCost = totalCost.add(Optional.ofNullable(costs.getPurchaseCost()).orElse(BigDecimal.ZERO));
        totalCost = totalCost.add(Optional.ofNullable(costs.getShippingCost()).orElse(BigDecimal.ZERO));
        totalCost = totalCost.add(Optional.ofNullable(costs.getFbaFee()).orElse(BigDecimal.ZERO));
        totalCost = totalCost.add(Optional.ofNullable(costs.getOtherCost()).orElse(BigDecimal.ZERO));

        // 计算关税（如果有关税率）
        BigDecimal purchaseCost = Optional.ofNullable(costs.getPurchaseCost()).orElse(BigDecimal.ZERO);
        if (costs.getTariffRate() != null && costs.getTariffRate().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tariff = purchaseCost.multiply(costs.getTariffRate());
            totalCost = totalCost.add(tariff);
        }

        // 计算利润
        BigDecimal profit = price.subtract(totalCost);

        // 计算利润率
        BigDecimal profitMargin = price.compareTo(BigDecimal.ZERO) > 0
                ? profit.divide(price, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        log.debug("ASIN: {} 利润计算完成。售价: {}, 总成本: {}, 利润: {}, 利润率: {}",
                asin, price, totalCost, profit, profitMargin);

        return new ProfitCalculationDto(profit, profitMargin, "计算成功");
    }


    private AsinCostsDto convertToDto(AsinCosts costs) {
        AsinCostsDto dto = new AsinCostsDto();
        dto.setAsin(costs.getAsin().getAsin());
        dto.setPurchaseCost(costs.getPurchaseCost());
        dto.setShippingCost(costs.getShippingCost());
        dto.setFbaFee(costs.getFbaFee());
        dto.setTariffRate(costs.getTariffRate());
        dto.setOtherCost(costs.getOtherCost());
        return dto;
    }

    /**
     * 利润计算结果的 DTO。
     */
    @Schema(name = "ProfitCalculationResponse", description = "利润计算返回结果")
    public static class ProfitCalculationDto {

        @Schema(description = "计算得到的利润金额", example = "12.34")
        private BigDecimal profit;

        @Schema(description = "利润率（0-1 之间的小数）", example = "0.2456")
        private BigDecimal profitMargin;

        @Schema(description = "状态描述，例如计算成功或错误原因", example = "计算成功")
        private String message;

        public ProfitCalculationDto(BigDecimal profit, BigDecimal profitMargin, String message) {
            this.profit = profit;
            this.profitMargin = profitMargin;
            this.message = message;
        }

        // Getters and Setters
        public BigDecimal getProfit() {
            return profit;
        }

        public void setProfit(BigDecimal profit) {
            this.profit = profit;
        }

        public BigDecimal getProfitMargin() {
            return profitMargin;
        }

        public void setProfitMargin(BigDecimal profitMargin) {
            this.profitMargin = profitMargin;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
