package com.amz.spyglass.controller;

import com.amz.spyglass.dto.AsinKeywordDto;
import com.amz.spyglass.dto.KeywordRankHistoryDto;
import com.amz.spyglass.dto.KeywordRankResponse;
import com.amz.spyglass.service.AsinKeywordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * V2.1 F-BIZ-001: ASIN 关键词管理的 API 控制器。
 * <p>
 * 提供 RESTful 接口用于管理 ASIN 的监控关键词。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@RestController
@RequestMapping("/api/v1/asins/{asin}/keywords")
@Tag(name = "Asin Keywords", description = "管理 ASIN 监控的关键词")
public class AsinKeywordsController {

    private final AsinKeywordsService asinKeywordsService;

    @Autowired
    public AsinKeywordsController(AsinKeywordsService asinKeywordsService) {
        this.asinKeywordsService = asinKeywordsService;
    }

            @Operation(summary = "为ASIN添加一个关键词", description = "为指定的ASIN添加一个新的监控关键词。",
                responses = {
                    @ApiResponse(responseCode = "201", description = "关键词创建成功", content = @Content(schema = @Schema(implementation = AsinKeywordDto.class))),
                    @ApiResponse(responseCode = "400", description = "请求体无效或缺少必填项"),
                    @ApiResponse(responseCode = "404", description = "指定的 ASIN 不存在")
                })
    @PostMapping
    public ResponseEntity<AsinKeywordDto> addKeyword(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Valid @RequestBody AsinKeywordDto keywordDto) {
        AsinKeywordDto newKeyword = asinKeywordsService.addKeyword(asin, keywordDto);
        return new ResponseEntity<>(newKeyword, HttpStatus.CREATED);
    }

            @Operation(summary = "获取ASIN的所有关键词", description = "检索指定ASIN的所有监控关键词，若未配置关键词则返回空数组。",
                responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AsinKeywordDto.class)))),
                    @ApiResponse(responseCode = "404", description = "指定的 ASIN 不存在")
                })
    @GetMapping
    public ResponseEntity<List<AsinKeywordDto>> getKeywords(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin) {
        List<AsinKeywordDto> keywords = asinKeywordsService.getKeywordsByAsin(asin);
        return ResponseEntity.ok(keywords);
    }

            @Operation(summary = "更新一个关键词", description = "更新指定ID的关键词信息，例如关键词文本或是否追踪。",
                responses = {
                    @ApiResponse(responseCode = "200", description = "关键词更新成功", content = @Content(schema = @Schema(implementation = AsinKeywordDto.class))),
                    @ApiResponse(responseCode = "400", description = "请求体无效或缺少必填项"),
                    @ApiResponse(responseCode = "404", description = "未找到指定的关键词记录")
                })
    @PutMapping("/{keywordId}")
    public ResponseEntity<AsinKeywordDto> updateKeyword(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Parameter(description = "关键词记录的唯一ID", required = true, example = "101") @PathVariable Long keywordId,
            @Valid @RequestBody AsinKeywordDto keywordDto) {
        // 在实践中，可以验证 keywordId 是否真的属于该 asin
        AsinKeywordDto updatedKeyword = asinKeywordsService.updateKeyword(keywordId, keywordDto);
        return ResponseEntity.ok(updatedKeyword);
    }

            @Operation(summary = "删除一个关键词", description = "删除指定ID的关键词。",
                responses = {
                    @ApiResponse(responseCode = "204", description = "删除成功"),
                    @ApiResponse(responseCode = "404", description = "未找到要删除的关键词")
                })
    @DeleteMapping("/{keywordId}")
    public ResponseEntity<Void> deleteKeyword(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Parameter(description = "关键词记录的唯一ID", required = true, example = "101") @PathVariable Long keywordId) {
        // 在实践中，可以验证 keywordId 是否真的属于该 asin
        asinKeywordsService.deleteKeyword(keywordId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "获取关键词排名历史", description = "获取指定关键词的历史排名数据。",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(array = @ArraySchema(schema = @Schema(implementation = KeywordRankHistoryDto.class)))),
                    @ApiResponse(responseCode = "404", description = "未找到指定的关键词记录")
            })
    @GetMapping("/{keywordId}/history")
    public ResponseEntity<List<KeywordRankHistoryDto>> getKeywordHistory(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Parameter(description = "关键词记录的唯一ID", required = true, example = "101") @PathVariable Long keywordId) {
        List<KeywordRankHistoryDto> history = asinKeywordsService.getKeywordRankHistory(keywordId);
        return ResponseEntity.ok(history);
    }

            @Operation(summary = "手动触发关键词排名抓取", description = "立即对指定关键词执行一次 Selenium 抓取，并返回自然排名/广告排名。",
                responses = {
                    @ApiResponse(responseCode = "200", description = "抓取成功", content = @Content(schema = @Schema(implementation = KeywordRankResponse.class))),
                    @ApiResponse(responseCode = "404", description = "ASIN 或关键词不存在"),
                    @ApiResponse(responseCode = "500", description = "抓取失败，请稍后重试")
                })
    @PostMapping("/{keywordId}/track-now")
    public ResponseEntity<KeywordRankResponse> trackKeywordNow(
            @Parameter(description = "亚马逊标准识别码", required = true, example = "B08N5WRWNW") @PathVariable String asin,
            @Parameter(description = "关键词记录的唯一ID", required = true, example = "101") @PathVariable Long keywordId) {
        KeywordRankResponse response = asinKeywordsService.triggerImmediateTracking(asin, keywordId);
        return ResponseEntity.ok(response);
    }
}
