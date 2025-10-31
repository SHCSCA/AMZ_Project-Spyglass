package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinHistoryResponse;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ASIN 商品历史数据控制器
 * 
 * 提供商品监控历史数据的查询接口，支持：
 * - 按商品 ID 查询历史价格变化
 * - 按商品 ID 查询历史 BSR 排名变化
 * - 按商品 ID 查询历史库存变化
 * - 时间序列数据分析支持（供前端折线图使用）
 * 
 * API 端点说明：
 * - GET /api/asin/{id}/history 获取指定商品的完整历史记录
 * 
 * 数据按抓取时间倒序排列，便于前端图表展示和趋势分析。
 * 
 * @author Spyglass Team
 * @version 2.0.0
 * @since 2024-12
 */
@RestController
@RequestMapping("/api/asin")
@Tag(name = "ASIN 数据查询", description = "提供查询指定 ASIN 历史抓取数据的功能")
public class AsinHistoryController {

    private final AsinHistoryRepository asinHistoryRepository;

    public AsinHistoryController(AsinHistoryRepository asinHistoryRepository) {
        this.asinHistoryRepository = asinHistoryRepository;
    }

    /**
     * 获取指定商品的完整历史监控记录
     * 
     * 返回指定ASIN商品的所有历史快照数据，包括：
     * - 价格变化历史（支持价格趋势图表）
     * - BSR排名变化历史（支持排名趋势分析）
     * - 库存变化历史（库存波动监控）
     * - 评分和评论数变化
     * 
     * @param asinId 商品监控记录ID
     * @return 历史记录列表（按时间倒序）
     */
    @GetMapping("/{id}/history")
    @Operation(summary = "获取指定 ASIN 的历史数据", description = "根据 ASIN 的唯一 ID，查询其在特定时间范围内的历史抓取快照，按时间降序排列。")
    @ApiResponse(responseCode = "200", description = "成功获取历史数据")
    @ApiResponse(responseCode = "404", description = "未找到指定 ID 的 ASIN")
    public List<AsinHistoryResponse> history(
            @Parameter(description = "要查询的 ASIN 的唯一 ID", required = true, example = "1") @PathVariable("id") Long asinId,
            @Parameter(description = "查询的时间范围，例如 '7d' (7天), '30d' (30天), '3m' (3个月)。默认为 30 天。", example = "30d")
            @RequestParam(defaultValue = "30d") String range) {

        Instant since = parseRange(range);
        List<AsinHistoryModel> rows = asinHistoryRepository.findByAsinIdAndSnapshotAtAfterOrderBySnapshotAtDesc(asinId, since);
        return rows.stream().map(this::toDto).collect(Collectors.toList());
    }

    private Instant parseRange(String range) {
        if (range == null || range.isBlank()) {
            return Instant.now().minus(30, ChronoUnit.DAYS);
        }
        try {
            char unit = range.charAt(range.length() - 1);
            long value = Long.parseLong(range.substring(0, range.length() - 1));
            switch (unit) {
                case 'd': return Instant.now().minus(value, ChronoUnit.DAYS);
                case 'm': return Instant.now().minus(value * 30, ChronoUnit.DAYS); // Approximate
                case 'y': return Instant.now().minus(value * 365, ChronoUnit.DAYS); // Approximate
                default: return Instant.now().minus(30, ChronoUnit.DAYS);
            }
        } catch (Exception e) {
            return Instant.now().minus(30, ChronoUnit.DAYS);
        }
    }

    private AsinHistoryResponse toDto(AsinHistoryModel h) {
        AsinHistoryResponse r = new AsinHistoryResponse();
        r.setId(h.getId());
        r.setAsinId(h.getAsin() == null ? null : h.getAsin().getId());
        r.setTitle(h.getTitle());
        r.setPrice(h.getPrice());
        r.setBsr(h.getBsr());
        r.setInventory(h.getInventory());
        r.setImageMd5(h.getImageMd5());
        r.setAplusMd5(h.getAplusMd5());
        r.setTotalReviews(h.getTotalReviews());
        r.setAvgRating(h.getAvgRating());
        r.setSnapshotAt(h.getSnapshotAt());
        return r;
    }
}
