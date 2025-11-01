package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AlertLogResponse;
import com.amz.spyglass.dto.PageResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/asin/{id}/alerts")
@Tag(name = "AsinAlertQuery", description = "指定 ASIN 告警查询接口 (F-API-005 补充)")
@Slf4j
@RequiredArgsConstructor
public class AsinAlertController {

    private final AlertLogRepository alertLogRepository;

    @GetMapping
    @Operation(summary = "分页查询指定 ASIN 的告警", description = "支持按类型、时间范围过滤。",
        responses = {@ApiResponse(responseCode = "200", description = "查询成功", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AlertLogResponse.class))))})
    public PageResponse<AlertLogResponse> list(
            @Parameter(description = "ASIN 主键 ID", example = "1") @PathVariable("id") Long asinId,
            @Parameter(description = "页码 (从0开始)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数", example = "50") @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "告警类型过滤", example = "PRICE_CHANGE") @RequestParam(required = false, name = "type") String type,
            @Parameter(description = "开始时间 ISO-8601", example = "2025-10-01T00:00:00Z") @RequestParam(required = false, name = "from") String from,
            @Parameter(description = "结束时间 ISO-8601", example = "2025-11-01T23:59:59Z") @RequestParam(required = false, name = "to") String to) {
        log.info("Query asin alerts asinId={}, page={}, size={}, type={}, from={}, to={}", asinId, page, size, type, from, to);
        Instant fromTs = parseInstantOrDefault(from, Instant.now().minusSeconds(30L * 24 * 3600));
        Instant toTs = parseInstantOrDefault(to, Instant.now());
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "alertAt"));
        var pageResult = alertLogRepository.findByAsinIdAndAlertAtBetween(asinId, fromTs, toTs, pageable);
        List<AlertLogResponse> items = pageResult.getContent().stream()
                .filter(a -> type == null || type.equalsIgnoreCase(a.getAlertType()))
                .map(this::toDto)
                .collect(Collectors.toList());
        PageResponse<AlertLogResponse> resp = new PageResponse<>();
        resp.setItems(items);
        resp.setTotal(pageResult.getTotalElements());
        resp.setPage(page);
        resp.setSize(size);
        return resp;
    }

    private Instant parseInstantOrDefault(String raw, Instant def) {
        if (raw == null || raw.isBlank()) return def;
        try { return Instant.parse(raw); } catch (Exception e) { return def; }
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
