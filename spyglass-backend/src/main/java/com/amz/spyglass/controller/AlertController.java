package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AlertLogResponse;
import com.amz.spyglass.model.alert.AlertLog;
import com.amz.spyglass.repository.alert.AlertLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import com.amz.spyglass.dto.PageResponse;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "AlertQuery", description = "警报日志查询接口 (F-API-005)")
@Slf4j
@RequiredArgsConstructor
public class AlertController {

    private final AlertLogRepository alertLogRepository;

    @GetMapping
    @Operation(summary = "获取最新告警日志", description = "按告警时间降序，限制返回条数。",
        responses = {
            @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AlertLogResponse.class))))
        })
    public PageResponse<AlertLogResponse> latest(
        @Parameter(description = "页码 (从0开始)", example = "0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "每页条数", example = "50") @RequestParam(defaultValue = "50") int size,
        @Parameter(description = "告警类型过滤", example = "PRICE_CHANGE") @RequestParam(name = "type", required = false) String type,
        @Parameter(description = "告警状态过滤（预留，当前忽略）", example = "NEW") @RequestParam(name = "status", required = false) String status) {
    log.info("Query alerts page={}, size={}, type={}, status={}", page, size, type, status);
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "alertAt"));
    var pageResult = alertLogRepository.findAll(pageable);
    var filtered = pageResult.getContent().stream()
        .filter(a -> type == null || type.equalsIgnoreCase(a.getAlertType()))
        .map(this::toDto).collect(Collectors.toList());
        PageResponse<AlertLogResponse> resp = new PageResponse<>();
        resp.setItems(filtered);
        resp.setTotal(pageResult.getTotalElements());
        resp.setPage(page);
        resp.setSize(size);
        resp.setTotalPages(pageResult.getTotalPages());
        resp.setHasNext(pageResult.hasNext());
        resp.setHasPrevious(pageResult.hasPrevious());
        return resp;
    }

    private AlertLogResponse toDto(AlertLog e) {
        AlertLogResponse r = new AlertLogResponse();
        r.setId(e.getId());
        r.setAsinId(e.getAsinId());
        r.setAsinCode(e.getAsinCode());
        r.setSite(e.getSite());
        r.setAlertType(e.getAlertType());
        r.setSeverity(e.getSeverity());
        r.setAlertAt(e.getAlertAt());
        r.setOldValue(e.getOldValue());
        r.setNewValue(e.getNewValue());
        r.setChangePercent(e.getChangePercent() == null ? null : e.getChangePercent().toPlainString());
        r.setRefId(e.getRefId());
        r.setContextJson(e.getContextJson());
        r.setMessage(e.getMessage());
        return r;
    }
}
