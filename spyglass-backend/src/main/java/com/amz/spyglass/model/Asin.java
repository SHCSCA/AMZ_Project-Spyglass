package com.amz.spyglass.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 实体：Asin（中文注释）
 * 描述：表示系统中需要监控的单个 ASIN 条目。
 * 关键字段：asin（ASIN 字符串），site（站点，如 US/UK），inventoryThreshold（库存告警阈值）
 */
@Entity
@Table(name = "asins")
public class Asin {

    /** 主键，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商品 ASIN，唯一且不能为空 */
    @Column(nullable = false, unique = true)
    private String asin;

    /** 所属站点，例如 US、UK 等 */
    @Column(nullable = false)
    private String site;

    /** 用户自定义昵称，便于在 UI 展示 */
    private String nickname;

    /** 库存报警阈值，低于该值则触发警报 */
    private Integer inventoryThreshold;

    /** 记录创建时间 */
    private Instant createdAt = Instant.now();

    /** 记录更新时间 */
    private Instant updatedAt = Instant.now();

    public Asin() {}

    // getters and setters
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
