package com.amz.spyglass.controller;

import com.amz.spyglass.model.AsinGroupModel;
import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.repository.AsinGroupRepository;
import com.amz.spyglass.repository.AsinRepository;
import com.amz.spyglass.dto.AsinResponse;
import com.amz.spyglass.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "ASIN 分组管理", description = "管理 ASIN 分组（创建、查询、查看分组下 ASIN 列表）")
public class GroupController {

    private final AsinGroupRepository groupRepository;
    private final AsinRepository asinRepository;

    public GroupController(AsinGroupRepository groupRepository, AsinRepository asinRepository) {
        this.groupRepository = groupRepository;
        this.asinRepository = asinRepository;
    }

    @PostMapping
    @Operation(summary = "创建分组", description = "创建一个新的 ASIN 分组")
    public ResponseEntity<AsinGroupModel> create(@RequestBody AsinGroupModel req) {
        AsinGroupModel g = new AsinGroupModel();
        g.setName(req.getName());
        g.setDescription(req.getDescription());
        g.setAsinCount(0); // 初始为0
        g.setCreatedAt(Instant.now());
        g.setUpdatedAt(Instant.now());
        AsinGroupModel saved = groupRepository.save(g);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    @Operation(summary = "分组列表", description = "分页获取全部分组")
    public PageResponse<AsinGroupModel> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<AsinGroupModel> p = groupRepository.findAll(pageable);
        PageResponse<AsinGroupModel> resp = new PageResponse<>();
        resp.setItems(p.getContent());
        resp.setTotal(p.getTotalElements());
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotalPages(p.getTotalPages());
        resp.setHasNext(p.hasNext());
        resp.setHasPrevious(p.hasPrevious());
        return resp;
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个分组", description = "根据分组 ID 查询")
    public ResponseEntity<AsinGroupModel> get(@PathVariable Long id) {
        return groupRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/asins")
    @Operation(summary = "查看分组下 ASIN 列表", description = "按分组分页列出 ASIN")
    public PageResponse<AsinResponse> listAsins(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<AsinModel> p = asinRepository.findAllByGroupId(id, pageable);
        List<AsinResponse> items = p.getContent().stream().map(this::toResponse).collect(Collectors.toList());
        PageResponse<AsinResponse> resp = new PageResponse<>();
        resp.setItems(items);
        resp.setTotal(p.getTotalElements());
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotalPages(p.getTotalPages());
        resp.setHasNext(p.hasNext());
        resp.setHasPrevious(p.hasPrevious());
        return resp;
    }

    private AsinResponse toResponse(AsinModel a) {
        AsinResponse r = new AsinResponse();
        r.setId(a.getId());
        r.setAsin(a.getAsin());
        r.setSite(a.getSite());
        r.setNickname(a.getNickname());
        r.setInventoryThreshold(a.getInventoryThreshold());
        r.setBrand(a.getBrand());
        if (a.getGroup() != null) {
            r.setGroupId(a.getGroup().getId());
            r.setGroupName(a.getGroup().getName());
        }
        r.setCreatedAt(a.getCreatedAt());
        r.setUpdatedAt(a.getUpdatedAt());
        return r;
    }
}
