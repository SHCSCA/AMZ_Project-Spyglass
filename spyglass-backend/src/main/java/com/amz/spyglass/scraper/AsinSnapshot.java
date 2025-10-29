package com.amz.spyglass.scraper;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 抓取快照 DTO（中文注释）
 * 用于在抓取层与服务层之间传递解析好的数据，不是 JPA 实体。
 */
public class AsinSnapshot {

    private String title;
    private BigDecimal price;
    private Integer bsr;
    private Integer inventory;
    private String imageMd5;
    private String aplusMd5;
    private Instant snapshotAt;

    public AsinSnapshot() {
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
