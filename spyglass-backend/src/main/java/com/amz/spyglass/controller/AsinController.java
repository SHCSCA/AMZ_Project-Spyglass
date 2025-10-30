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

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST 控制器：Asin 管理（中文注释）
 * 提供 ASIN 的基本 CRUD 接口，供前端（AI 生成或手写）使用。
 */
@RestController
@RequestMapping("/api/asin")
@Tag(name = "ASIN 管理", description = "提供对监控的 ASIN 列表进行增、删、改、查的核心接口")
public class AsinController {

    private final AsinRepository asinRepository;

    public AsinController(AsinRepository asinRepository) {
        this.asinRepository = asinRepository;
    }

    @GetMapping
    @Operation(summary = "获取所有监控的 ASIN 列表", description = "返回一个包含所有已添加 ASIN 简要信息的列表。")
    @ApiResponse(responseCode = "200", description = "成功获取列表")
    public List<AsinResponse> list() {
        return asinRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @PostMapping
    @Operation(summary = "添加一个新的 ASIN 进行监控", description = "根据传入的 ASIN、站点等信息，创建一个新的监控任务。")
    @ApiResponse(responseCode = "200", description = "ASIN 创建成功", content = @Content(schema = @Schema(implementation = AsinResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数无效（例如ASIN为空或格式错误）")
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

    @DeleteMapping("/{id}")
    @Operation(summary = "删除一个 ASIN", description = "根据提供的主键 ID 删除一个 ASIN 及其相关的监控任务。")
    @ApiResponse(responseCode = "204", description = "删除成功")
    @ApiResponse(responseCode = "404", description = "未找到指定 ID 的 ASIN")
    public ResponseEntity<Void> delete(
            @Parameter(description = "要删除的 ASIN 的唯一 ID", required = true, example = "1") @PathVariable Long id) {
        if (!asinRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        asinRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/config")
    @Operation(summary = "更新指定 ASIN 的配置", description = "允许更新一个已存在 ASIN 的昵称、库存阈值等配置信息。")
    @ApiResponse(responseCode = "200", description = "更新成功", content = @Content(schema = @Schema(implementation = AsinResponse.class)))
    @ApiResponse(responseCode = "404", description = "未找到指定 ID 的 ASIN")
    public ResponseEntity<AsinResponse> updateConfig(
            @Parameter(description = "要更新的 ASIN 的唯一 ID", required = true, example = "1") @PathVariable Long id,
            @RequestBody AsinRequest req) {
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
