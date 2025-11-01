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
    public List<AlertLogResponse> latest(
            @Parameter(description = "返回条数限制", example = "50") @RequestParam(name = "limit", defaultValue = "50") int limit,
            @Parameter(description = "告警状态过滤（预留，当前忽略）", example = "NEW") @RequestParam(name = "status", required = false) String status) {
        log.info("Query latest alerts limit={}, status={} (status currently ignored)", limit, status);
        return alertLogRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "alertAt")))
                .stream().map(this::toDto).collect(Collectors.toList());
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
