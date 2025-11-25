package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 关键词排名历史记录 DTO
 */
@Data
@Schema(description = "关键词排名历史记录")
public class KeywordRankHistoryDto {

    @Schema(description = "记录ID", example = "1")
    private Long id;

    @Schema(description = "抓取日期")
    private LocalDate scrapeDate;

    @Schema(description = "自然排名，未找到时为 -1", example = "12")
    private Integer naturalRank;

    @Schema(description = "广告排名，未找到时为 -1", example = "5")
    private Integer sponsoredRank;

    @Schema(description = "排名所在页码，未找到时为 -1", example = "1")
    private Integer page;
}
