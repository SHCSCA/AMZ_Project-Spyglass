package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "标准错误响应结构")
public class ErrorResponse {
    @Schema(description = "错误代码", example = "INVALID_PARAM")
    private String error;
    @Schema(description = "人类可读的错误描述")
    private String message;
    @Schema(description = "附加详情，可选")
    private Map<String, Object> details;
    @Schema(description = "时间戳 (UTC ISO-8601)")
    private Instant timestamp;
}
