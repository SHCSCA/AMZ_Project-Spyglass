package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinHistoryResponse;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ASIN商品历史数据控制器
 * 
 * 提供商品监控历史数据的查询接口，支持：
 * - 按商品ID查询历史价格变化
 * - 按商品ID查询历史BSR排名变化  
 * - 按商品ID查询历史库存变化
 * - 时间序列数据分析支持
 * 
 * API端点说明：
 * • GET /api/asin/{id}/history - 获取指定商品的完整历史记录
 * 
 * 数据按抓取时间倒序排列，便于前端图表展示和趋势分析
 * 
 * @author Spyglass Team  
 * @version 2.0.0
 * @since 2024-12
 */
@RestController
@RequestMapping("/api/asin")
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
    public List<AsinHistoryResponse> history(@PathVariable("id") Long asinId) {
        List<AsinHistoryModel> rows = asinHistoryRepository.findByAsinIdOrderBySnapshotAtDesc(asinId);
        return rows.stream().map(this::toDto).collect(Collectors.toList());
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
