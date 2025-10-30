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
 * Asin 历史快照接口（中文注释）
 * 提供按 ASIN 查询历史快照的只读接口，支持按时间范围筛选。
 */
@RestController
@RequestMapping("/api/asin")
@Tag(name = "ASIN 数据查询", description = "提供查询指定 ASIN 历史抓取数据的功能")
public class AsinHistoryController {

    private final AsinHistoryRepository asinHistoryRepository;

    public AsinHistoryController(AsinHistoryRepository asinHistoryRepository) {
        this.asinHistoryRepository = asinHistoryRepository;
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "获取指定 ASIN 的历史数据", description = "根据 ASIN 的唯一 ID，查询其在特定时间范围内的历史抓取快照，按时间降序排列。")
    @ApiResponse(responseCode = "200", description = "成功获取历史数据")
    @ApiResponse(responseCode = "404", description = "未找到指定 ID 的 ASIN")
    public List<AsinHistoryResponse> history(
            @Parameter(description = "要查询的 ASIN 的唯一 ID", required = true, example = "1") @PathVariable("id") Long asinId,
            @Parameter(description = "查询的时间范围，例如 '7d' (7天), '30d' (30天), '3m' (3个月)。默认为 30 天。", example = "30d")
            @RequestParam(value = "range", defaultValue = "30d") String range) {

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
        r.setSnapshotAt(h.getSnapshotAt());
        return r;
    }
}
