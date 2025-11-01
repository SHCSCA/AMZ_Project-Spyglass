package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Schema(description = "差评 / 评论记录响应 DTO")
public class ReviewAlertResponse {
    private Long id;
    private Long asinId;
    private String reviewId;
    private Integer rating;
    private LocalDate reviewDate;
    private String reviewText;
    private Instant alertAt;
}
