package com.amz.spyglass.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * ASIN 历史快照实体（中文注释）
 * 用于保存每次抓取的快照数据，例如价格、BSR、库存、图片 MD5、A+ MD5 等。
 * 为了简单，当前只保存 title 和 snapshotAt；后续可以扩展更多字段。
 */
@Entity
@Table(name = "asin_history")
public class AsinHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 与 Asin 的多对一关系
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asin_id")
    private Asin asin;

    // 页面标题（示例字段）
    private String title;

    // 价格（保留两位小数），可为空
    private BigDecimal price;

    // BSR（可为空）
    private Integer bsr;

    // 库存数量快照（可为空）
    private Integer inventory;

    // 主图 MD5（可为空）
    private String imageMd5;

    // A+ 内容 MD5（可为空）
    private String aplusMd5;

    // 抓取时间戳
    private Instant snapshotAt;

    // --- getters / setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Asin getAsin() {
        return asin;
    }

    public void setAsin(Asin asin) {
        this.asin = asin;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getBsr() {
        return bsr;
    }

    public void setBsr(Integer bsr) {
        this.bsr = bsr;
    }

    public Integer getInventory() {
        return inventory;
    }

    public void setInventory(Integer inventory) {
        this.inventory = inventory;
    }

    public String getImageMd5() {
        return imageMd5;
    }

    public void setImageMd5(String imageMd5) {
        this.imageMd5 = imageMd5;
    }

    public String getAplusMd5() {
        return aplusMd5;
    }

    public void setAplusMd5(String aplusMd5) {
        this.aplusMd5 = aplusMd5;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(Instant snapshotAt) {
        this.snapshotAt = snapshotAt;
    }
}
