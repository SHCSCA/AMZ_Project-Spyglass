package com.amz.spyglass.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "统一分页响应结构")
public class PageResponse<T> {
    @Schema(description = "当前页数据列表")
    private List<T> items;
    @Schema(description = "总记录数")
    private long total;
    @Schema(description = "当前页码 (从0开始)")
    private int page;
    @Schema(description = "每页大小")
    private int size;
}
