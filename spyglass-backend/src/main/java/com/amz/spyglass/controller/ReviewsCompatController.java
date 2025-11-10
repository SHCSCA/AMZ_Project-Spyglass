package com.amz.spyglass.controller;

import com.amz.spyglass.dto.ReviewAlertResponse;
import com.amz.spyglass.dto.PageResponse;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.model.alert.ReviewAlert;
import com.amz.spyglass.repository.AsinRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * 兼容旧版客户端请求：/api/reviews?asin=XXX
 *
 * 当前主实现使用 /api/asin/{id}/reviews，部分旧客户端仍会调用 /api/reviews?asin=ASIN
 * 为了平滑迁移，这里提供一个轻量兼容层，将 asin -> asinId 并复用 ReviewAlertRepository 返回数据。
 */
@RestController
@RequestMapping("/api/reviews")
@Tag(name = "ReviewQueryCompat", description = "兼容 /api/reviews?asin=... 的查询接口")
@Slf4j
@RequiredArgsConstructor
public class ReviewsCompatController {

    private final AsinRepository asinRepository;
    private final ReviewAlertRepository reviewAlertRepository;

    @GetMapping
    @Operation(summary = "兼容查询：根据 asin (字符串) 查询评论", description = "向后兼容的查询接口：接受 asin=ASIN 的查询并返回分页结果（与 /api/asin/{id}/reviews 行为一致）。",
        responses = {@ApiResponse(responseCode = "200", description = "查询成功", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReviewAlertResponse.class))))})
    public ResponseEntity<PageResponse<ReviewAlertResponse>> compatList(
            @Parameter(description = "商品 ASIN 字符串，例如 B08XXXXX", required = true) @RequestParam(name = "asin") String asin,
            @Parameter(description = "过滤参数：negative (仅返回 1-3 星)") @RequestParam(name = "rating", required = false) String ratingFilter,
            @Parameter(description = "页码 (从0开始)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数 (最大200)") @jakarta.validation.constraints.Max(200) @RequestParam(defaultValue = "50") int size) {

        var opt = asinRepository.findByAsin(asin);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AsinModel a = opt.get();
        if (size > 200) throw new IllegalArgumentException("size 超过最大限制 200");
        var pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "reviewDate"));
        var pageResult = reviewAlertRepository.findByAsinId(a.getId(), pageable);
        var stream = pageResult.getContent().stream();
        if (ratingFilter != null && "negative".equalsIgnoreCase(ratingFilter)) {
            stream = stream.filter(r -> r.getRating() != null && r.getRating() <= 3);
        }
        var filtered = stream.map(this::toDto).collect(Collectors.toList());
        PageResponse<ReviewAlertResponse> resp = new PageResponse<>();
        resp.setItems(filtered);
        resp.setTotal(pageResult.getTotalElements());
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotalPages(pageResult.getTotalPages());
        resp.setHasNext(pageResult.hasNext());
        resp.setHasPrevious(pageResult.hasPrevious());
        return ResponseEntity.ok(resp);
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
