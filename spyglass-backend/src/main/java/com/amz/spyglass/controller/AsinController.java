package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinRequest;
import com.amz.spyglass.dto.AsinResponse;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import com.amz.spyglass.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
@Tag(name = "ASIN 管理", description = "提供对监控的 ASIN 列表进行增、删、改、查的核心接口")
public class AsinController {

    private static final Logger log = LoggerFactory.getLogger(AsinController.class);
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
    @Operation(summary = "获取所有监控的 ASIN 列表 (分页)", description = "支持分页与按创建时间排序。",
        responses = {
            @ApiResponse(responseCode = "200", description = "成功获取列表", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AsinResponse.class))))
        })
    public PageResponse<AsinResponse> list(
            @Parameter(description = "页码 (从0开始)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数", example = "50") @RequestParam(defaultValue = "50") int size) {
        log.info("Request to list ASINs page={}, size={}", page, size);
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    var pageResult = asinRepository.findAll(pageable);
    List<AsinResponse> items = pageResult.getContent().stream().map(this::toResponse).collect(Collectors.toList());
        PageResponse<AsinResponse> resp = new PageResponse<>();
        resp.setItems(items);
        resp.setTotal(pageResult.getTotalElements());
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotalPages(pageResult.getTotalPages());
        resp.setHasNext(pageResult.hasNext());
        resp.setHasPrevious(pageResult.hasPrevious());
        log.info("Found {} ASINs (total {} records) in page {}", items.size(), resp.getTotal(), page);
        return resp;
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
    @Operation(summary = "添加一个新的 ASIN 进行监控", description = "根据传入的 ASIN、站点等信息，创建一个新的监控任务。",
        responses = {
            @ApiResponse(responseCode = "200", description = "ASIN 创建成功", content = @Content(schema = @Schema(implementation = AsinResponse.class))),
            @ApiResponse(responseCode = "400", description = "请求参数无效（例如ASIN为空或格式错误）")
        })
    public ResponseEntity<AsinResponse> create(@Valid @RequestBody AsinRequest req) {
        log.info("Request to create ASIN: {}", req);
        AsinModel a = new AsinModel();
        a.setAsin(req.getAsin());
        a.setSite(req.getSite());
        a.setNickname(req.getNickname());
        a.setInventoryThreshold(req.getInventoryThreshold());
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        AsinModel saved = asinRepository.save(a);
        log.info("Successfully created ASIN with ID: {}", saved.getId());
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
    @Operation(summary = "删除一个 ASIN", description = "根据提供的主键 ID 删除一个 ASIN 及其相关的监控任务。",
        responses = {
            @ApiResponse(responseCode = "204", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "未找到指定 ID 的 ASIN")
        })
    public ResponseEntity<Void> delete(
            @Parameter(description = "要删除的 ASIN 的唯一 ID", required = true, example = "1") @PathVariable Long id) {
        log.info("Request to delete ASIN with ID: {}", id);
        if (!asinRepository.existsById(id)) {
            log.warn("ASIN with ID: {} not found for deletion", id);
            return ResponseEntity.notFound().build();
        }
        asinRepository.deleteById(id);
        log.info("Successfully deleted ASIN with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/config")
    @Operation(summary = "更新指定 ASIN 的配置", description = "允许更新一个已存在 ASIN 的昵称、库存阈值等配置信息。",
        responses = {
            @ApiResponse(responseCode = "200", description = "更新成功", content = @Content(schema = @Schema(implementation = AsinResponse.class))),
            @ApiResponse(responseCode = "404", description = "未找到指定 ID 的 ASIN")
        })
    public ResponseEntity<AsinResponse> updateConfig(
            @Parameter(description = "要更新的 ASIN 的唯一 ID", required = true, example = "1") @PathVariable Long id,
            @RequestBody AsinRequest req) {
        log.info("Request to update config for ASIN ID: {}, with data: {}", id, req);
        return asinRepository.findById(id).map(a -> {
            a.setInventoryThreshold(req.getInventoryThreshold());
            a.setNickname(req.getNickname());
            a.setSite(req.getSite());
            a.setUpdatedAt(Instant.now());
            AsinModel saved = asinRepository.save(a);
            log.info("Successfully updated config for ASIN ID: {}", id);
            return ResponseEntity.ok(toResponse(saved));
        }).orElseGet(() -> {
            log.warn("ASIN with ID: {} not found for config update", id);
            return ResponseEntity.notFound().build();
        });
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
