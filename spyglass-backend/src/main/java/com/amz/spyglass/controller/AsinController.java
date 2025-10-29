package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinRequest;
import com.amz.spyglass.dto.AsinResponse;
import com.amz.spyglass.model.Asin;
import com.amz.spyglass.repository.AsinRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST 控制器：Asin 管理（中文注释）
 * 提供 ASIN 的基本 CRUD 接口，供前端（AI 生成或手写）使用。
 * 端点：
 *  - GET /api/asin : 列表
 *  - POST /api/asin : 新增
 *  - DELETE /api/asin/{id} : 删除
 *  - PUT /api/asin/{id}/config : 更新配置（如库存阈值）
 */
@RestController
@RequestMapping("/api/asin")
public class AsinController {

    private final AsinRepository asinRepository;

    public AsinController(AsinRepository asinRepository) {
        this.asinRepository = asinRepository;
    }

    /**
     * 获取所有 ASIN 列表（简化的响应 DTO）
     */
    @GetMapping
    public List<AsinResponse> list() {
        return asinRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 创建新的 ASIN 监控项（参数校验由 AsinRequest 上的注解完成）
     */
    @PostMapping
    public ResponseEntity<AsinResponse> create(@Valid @RequestBody AsinRequest req) {
        Asin a = new Asin();
        a.setAsin(req.getAsin());
        a.setSite(req.getSite());
        a.setNickname(req.getNickname());
        a.setInventoryThreshold(req.getInventoryThreshold());
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        Asin saved = asinRepository.save(a);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * 删除 ASIN
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        asinRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 更新 ASIN 的配置信息（目前仅支持部分字段）
     */
    @PutMapping("/{id}/config")
    public ResponseEntity<AsinResponse> updateConfig(@PathVariable Long id, @RequestBody AsinRequest req) {
        return asinRepository.findById(id).map(a -> {
            a.setInventoryThreshold(req.getInventoryThreshold());
            a.setNickname(req.getNickname());
            a.setSite(req.getSite());
            a.setUpdatedAt(Instant.now());
            Asin saved = asinRepository.save(a);
            return ResponseEntity.ok(toResponse(saved));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private AsinResponse toResponse(Asin a) {
        AsinResponse r = new AsinResponse();
        r.setId(a.getId());
        r.setAsin(a.getAsin());
        r.setSite(a.getSite());
        r.setNickname(a.getNickname());
        r.setInventoryThreshold(a.getInventoryThreshold());
        r.setCreatedAt(a.getCreatedAt());
        r.setUpdatedAt(a.getUpdatedAt());
        return r;
    }
}
