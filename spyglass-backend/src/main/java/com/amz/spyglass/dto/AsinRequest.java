package com.amz.spyglass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO：AsinRequest（中文注释）
 * 用途：接收前端请求创建或更新 ASIN 的输入参数。
 * 验证规则：asin 与 site 不能为空，inventoryThreshold 必须提供（整型）。
 */
public class AsinRequest {

    @NotBlank
    private String asin;

    @NotBlank
    private String site;

    private String nickname;

    @NotNull
    private Integer inventoryThreshold;

    public String getAsin() { return asin; }
    public void setAsin(String asin) { this.asin = asin; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getInventoryThreshold() { return inventoryThreshold; }
    public void setInventoryThreshold(Integer inventoryThreshold) { this.inventoryThreshold = inventoryThreshold; }
}
