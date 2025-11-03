package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.math.BigDecimal;

@Data
@Schema(description = "告警日志响应 DTO")
public class AlertLogResponse {
    @Schema(description = "告警主键 ID")
    private Long id;
    @Schema(description = "ASIN 记录主键")
    private Long asinId;
    @Schema(description = "ASIN 代码")
    private String asinCode;
    @Schema(description = "站点")
    private String site;
    @Schema(description = "告警类型，例如 PRICE_CHANGE / INVENTORY_THRESHOLD / REVIEW_NEGATIVE")
    private String alertType;
    @Schema(description = "严重级别，默认 INFO")
    private String severity;
    @Schema(description = "触发时间戳")
    private Instant alertAt;
    @Schema(description = "旧值（必要时）", nullable = true)
    private String oldValue;
    @Schema(description = "新值（必要时）", nullable = true)
    private String newValue;
    @Schema(description = "变更百分比数值，正数=上涨，负数=下降，单位为百分比，例如 -25.00 表示下降25%", nullable = true)
    private BigDecimal changePercent;
    @Schema(description = "引用的关联记录ID，例如 ReviewAlert 的 ID", nullable = true)
    private Long refId;
    @Schema(description = "上下文 JSON 原始字符串", nullable = true)
    private String contextJson;
    @Schema(description = "格式化消息", nullable = true)
    private String message;
}
