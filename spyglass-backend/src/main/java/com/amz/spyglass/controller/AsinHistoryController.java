package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinHistoryResponse;
import com.amz.spyglass.model.AsinHistory;
import com.amz.spyglass.repository.AsinHistoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Asin 历史快照接口（中文注释）
 * 提供按 ASIN 查询历史快照的简单只读接口，返回按时间降序排列的记录列表。
 */
@RestController
@RequestMapping("/api/asin")
public class AsinHistoryController {

    private final AsinHistoryRepository asinHistoryRepository;

    public AsinHistoryController(AsinHistoryRepository asinHistoryRepository) {
        this.asinHistoryRepository = asinHistoryRepository;
    }

    @GetMapping("/{id}/history")
    public List<AsinHistoryResponse> history(@PathVariable("id") Long asinId) {
        List<AsinHistory> rows = asinHistoryRepository.findByAsinIdOrderBySnapshotAtDesc(asinId);
        return rows.stream().map(this::toDto).collect(Collectors.toList());
    }

    private AsinHistoryResponse toDto(AsinHistory h) {
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
