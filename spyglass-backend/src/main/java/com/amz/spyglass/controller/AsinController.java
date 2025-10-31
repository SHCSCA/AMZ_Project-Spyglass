package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinRequest;
import com.amz.spyglass.dto.AsinResponse;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ASIN商品管理REST控制器
 * 
 * 提供亚马逊产品ASIN的完整CRUD管理接口，支持：
 * - 商品监控列表管理
 * - 监控配置参数设置（库存阈值、价格变动等）
 * - 监控状态控制（启用/禁用）
 * 
 * API端点说明：
 * • GET    /api/asin         - 获取所有监控商品列表
 * • POST   /api/asin         - 添加新的商品监控
 * • DELETE /api/asin/{id}    - 删除指定商品监控
 * • PUT    /api/asin/{id}/config - 更新监控配置（库存阈值、告警设置等）
 * 
 * 集成OpenAPI文档，支持前端代码自动生成
 * 
 * @author Spyglass Team
 * @version 2.0.0
 * @since 2024-12
 */
@RestController
@RequestMapping("/api/asin")
public class AsinController {

    private final AsinRepository asinRepository;

    public AsinController(AsinRepository asinRepository) {
        this.asinRepository = asinRepository;
    }

    /**
     * 获取所有监控中的ASIN商品列表
     * 
     * 返回当前系统中所有正在监控的亚马逊商品信息，
     * 包括商品基本信息和监控配置参数
     * 
     * @return 监控商品列表（DTO格式）
     */
    @GetMapping
    public List<AsinResponse> list() {
        return asinRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 添加新的ASIN商品监控
     * 
     * 创建新的商品监控项，支持配置：
     * - 商品ASIN和站点信息
     * - 自定义商品昵称
     * - 库存告警阈值
     * 
     * @param req 新增商品监控请求参数（自动校验）
     * @return 创建成功的商品监控信息
     */
    @PostMapping
    public ResponseEntity<AsinResponse> create(@Valid @RequestBody AsinRequest req) {
        AsinModel a = new AsinModel();
        a.setAsin(req.getAsin());
        a.setSite(req.getSite());
        a.setNickname(req.getNickname());
        a.setInventoryThreshold(req.getInventoryThreshold());
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        AsinModel saved = asinRepository.save(a);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * 删除指定的ASIN商品监控
     * 
     * 删除指定ID的商品监控项，停止对该商品的数据抓取和告警
     * 注意：历史数据不会被删除，仅停止后续监控
     * 
     * @param id 商品监控记录ID
     * @return HTTP 204 No Content
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
            AsinModel saved = asinRepository.save(a);
            return ResponseEntity.ok(toResponse(saved));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private AsinResponse toResponse(AsinModel a) {
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
