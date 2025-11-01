package com.amz.spyglass.controller;

import com.amz.spyglass.dto.ReviewAlertResponse;
import com.amz.spyglass.model.alert.ReviewAlert;
import com.amz.spyglass.repository.alert.ReviewAlertRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/asin/{id}/reviews")
@Tag(name = "ReviewQuery", description = "差评 / 评论查询接口 (F-API-006)")
@Slf4j
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewAlertRepository reviewAlertRepository;

    @GetMapping
    @Operation(summary = "查询指定 ASIN 的评论记录", description = "当 rating=negative 时，仅返回 1-3 星差评。",
        responses = {
            @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReviewAlertResponse.class))))
        })
    public List<ReviewAlertResponse> list(
            @Parameter(description = "ASIN 主键 ID", example = "1") @PathVariable("id") Long asinId,
            @Parameter(description = "过滤参数：negative (仅返回 1-3 星)", example = "negative") @RequestParam(name = "rating", required = false) String ratingFilter) {
        log.info("Query reviews for asinId={}, ratingFilter={}", asinId, ratingFilter);
        return reviewAlertRepository.findAll().stream()
                .filter(r -> r.getAsinId().equals(asinId))
                .filter(r -> ratingFilter == null || !"negative".equalsIgnoreCase(ratingFilter) || (r.getRating() != null && r.getRating() <= 3))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ReviewAlertResponse toDto(ReviewAlert r) {
        ReviewAlertResponse dto = new ReviewAlertResponse();
        dto.setId(r.getId());
        dto.setAsinId(r.getAsinId());
        dto.setReviewId(r.getReviewId());
        dto.setRating(r.getRating());
        dto.setReviewDate(r.getReviewDate());
        dto.setReviewText(r.getReviewText());
        dto.setAlertAt(r.getAlertAt());
        return dto;
    }
}
