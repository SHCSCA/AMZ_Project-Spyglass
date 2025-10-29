package com.amz.spyglass.dto;

import java.time.Instant;

/**
 * DTO：AsinResponse（中文注释）
 * 用途：返回给前端的 ASIN 对象视图，包含创建/更新时间与基础配置字段。
 */
public class AsinResponse {
    private Long id;
    private String asin;
    private String site;
    private String nickname;
    private Integer inventoryThreshold;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAsin() { return asin; }
    public void setAsin(String asin) { this.asin = asin; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getInventoryThreshold() { return inventoryThreshold; }
    public void setInventoryThreshold(Integer inventoryThreshold) { this.inventoryThreshold = inventoryThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
