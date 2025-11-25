package com.amz.spyglass.dto;

import com.amz.spyglass.model.KeywordRankHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 手动触发关键词排名抓取的响应 DTO。
 */
@Schema(description = "关键词排名抓取结果")
public class KeywordRankResponse {

    @Schema(description = "关键词记录的唯一ID", example = "101")
    private Long keywordId;

    @Schema(description = "ASIN 编码", example = "B08N5WRWNW")
    private String asin;

    @Schema(description = "触发抓取的关键词", example = "ergonomic office chair")
    private String keyword;

    @Schema(description = "自然排名，未找到时为 -1", example = "12")
    private Integer naturalRank;

    @Schema(description = "广告排名，未找到时为 -1", example = "5")
    private Integer sponsoredRank;

    @Schema(description = "排名所在页码，未找到时为 -1", example = "1")
    private Integer page;

    @Schema(description = "抓取日期")
    private LocalDate scrapeDate;

    @Schema(description = "本次触发是否成功")
    private boolean success;

    @Schema(description = "附加说明，例如失败原因")
    private String message;

    public static KeywordRankResponse success(Long keywordId, String asin, String keyword, KeywordRankHistory history) {
        KeywordRankResponse response = new KeywordRankResponse();
        response.setKeywordId(keywordId);
        response.setAsin(asin);
        response.setKeyword(keyword);
        response.setNaturalRank(history.getNaturalRank());
        response.setSponsoredRank(history.getSponsoredRank());
        response.setPage(history.getPage());
        response.setScrapeDate(history.getScrapeDate());
        response.setSuccess(true);
        response.setMessage("抓取成功");
        return response;
    }

    public static KeywordRankResponse failure(Long keywordId, String asin, String keyword, String message) {
        KeywordRankResponse response = new KeywordRankResponse();
        response.setKeywordId(keywordId);
        response.setAsin(asin);
        response.setKeyword(keyword);
        response.setNaturalRank(-1);
        response.setSponsoredRank(-1);
        response.setPage(-1);
        response.setScrapeDate(LocalDate.now());
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    public Long getKeywordId() {
        return keywordId;
    }

    public void setKeywordId(Long keywordId) {
        this.keywordId = keywordId;
    }

    public String getAsin() {
        return asin;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getNaturalRank() {
        return naturalRank;
    }

    public void setNaturalRank(Integer naturalRank) {
        this.naturalRank = naturalRank;
    }

    public Integer getSponsoredRank() {
        return sponsoredRank;
    }

    public void setSponsoredRank(Integer sponsoredRank) {
        this.sponsoredRank = sponsoredRank;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public LocalDate getScrapeDate() {
        return scrapeDate;
    }

    public void setScrapeDate(LocalDate scrapeDate) {
        this.scrapeDate = scrapeDate;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
