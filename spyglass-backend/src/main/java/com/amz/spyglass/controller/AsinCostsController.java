package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinCostsDto;
import com.amz.spyglass.service.AsinCostsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * V2.1 F-BIZ-002: ASIN 成本和利润计算的 API 控制器。
 * <p>
 * 提供 RESTful 接口用于管理 ASIN 的成本配置和执行利润计算。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@RestController
@RequestMapping("/api/v1/asins/{asin}/costs")
@Tag(name = "Asin Costs & Profit", description = "管理 ASIN 成本配置并计算利润")
public class AsinCostsController {

    private final AsinCostsService asinCostsService;

    @Autowired
    public AsinCostsController(AsinCostsService asinCostsService) {
        this.asinCostsService = asinCostsService;
    }

    @Operation(summary = "创建或更新ASIN的成本配置",
            description = "为指定的ASIN创建或更新成本数据。如果已存在，则覆盖。",
            responses = {
                    @ApiResponse(responseCode = "200", description = "成本配置已成功保存", content = @Content(schema = @Schema(implementation = AsinCostsDto.class))),
                    @ApiResponse(responseCode = "400", description = "请求体无效"),
                    @ApiResponse(responseCode = "404", description = "ASIN不存在")
            })
    @PostMapping
    public ResponseEntity<AsinCostsDto> createOrUpdateCosts(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Valid @RequestBody AsinCostsDto costsDto) {
        AsinCostsDto savedCosts = asinCostsService.createOrUpdateCosts(asin, costsDto);
        return ResponseEntity.ok(savedCosts);
    }

    @Operation(summary = "获取ASIN的成本配置",
            description = "检索指定ASIN的成本数据。",
            responses = {
                    @ApiResponse(responseCode = "200", description = "成功检索到成本配置", content = @Content(schema = @Schema(implementation = AsinCostsDto.class))),
                    @ApiResponse(responseCode = "404", description = "未找到指定ASIN的成本配置")
            })
    @GetMapping
    public ResponseEntity<AsinCostsDto> getCosts(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin) {
        return asinCostsService.getCostsByAsin(asin)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "删除ASIN的成本配置",
            description = "删除指定ASIN的成本数据。",
            responses = {
                    @ApiResponse(responseCode = "204", description = "成本配置已成功删除"),
                    @ApiResponse(responseCode = "404", description = "未找到要删除的成本配置")
            })
    @DeleteMapping
    public ResponseEntity<Void> deleteCosts(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin) {
        asinCostsService.deleteCosts(asin);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "计算ASIN的利润",
            description = "根据给定的售价和已配置的成本，计算产品的利润和利润率。",
            responses = {
                    @ApiResponse(responseCode = "200", description = "利润计算成功", content = @Content(schema = @Schema(implementation = AsinCostsService.ProfitCalculationDto.class))),
                    @ApiResponse(responseCode = "400", description = "售价无效或成本未配置")
            })
    @GetMapping("/calculate-profit")
    public ResponseEntity<AsinCostsService.ProfitCalculationDto> calculateProfit(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Parameter(description = "要计算的售价", required = true, example = "49.99") @RequestParam BigDecimal price) {
        AsinCostsService.ProfitCalculationDto result = asinCostsService.calculateProfit(asin, price);
        if (result.getProfit() == null) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
