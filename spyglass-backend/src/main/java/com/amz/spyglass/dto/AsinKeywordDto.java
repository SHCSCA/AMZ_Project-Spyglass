package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * V2.1 F-BIZ-001: ASIN 关键词的数据传输对象 (DTO)。
 * 用于在 API 层传输关键词信息。
 *
 * @author AI Assistant
 * @version 2.1.0
 * @since 2025-11-23
 */
@Schema(description = "ASIN 关键词的数据传输对象")
public class AsinKeywordDto {

    @Schema(description = "关键词记录的唯一ID", example = "101", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank
    @Schema(description = "要监控的关键词", example = "ergonomic office chair")
    private String keyword;

    @NotNull
    @Schema(description = "是否开启排名追踪", example = "true")
    private Boolean isTracked;

    @Schema(description = "所属ASIN", example = "B08N5WRWNW", accessMode = Schema.AccessMode.READ_ONLY)
    private String asin;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getIsTracked() {
        return isTracked;
    }

    public void setIsTracked(Boolean tracked) {
        isTracked = tracked;
    }

    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }
}
