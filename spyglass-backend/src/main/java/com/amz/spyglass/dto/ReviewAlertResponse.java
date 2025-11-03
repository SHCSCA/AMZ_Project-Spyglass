package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Schema(description = "差评 / 评论记录响应 DTO")
public class ReviewAlertResponse {
    @Schema(description = "评论记录主键 ID")
    private Long id;
    @Schema(description = "关联 ASIN 主键 ID")
    private Long asinId;
    @Schema(description = "评论唯一 ID", nullable = true)
    private String reviewId;
    @Schema(description = "评分 (1-5)", nullable = true)
    private Integer rating;
    @Schema(description = "评论日期", nullable = true)
    private LocalDate reviewDate;
    @Schema(description = "评论正文", nullable = true)
    private String reviewText;
    @Schema(description = "告警生成时间 (UTC ISO-8601)")
    private Instant alertAt;
}
