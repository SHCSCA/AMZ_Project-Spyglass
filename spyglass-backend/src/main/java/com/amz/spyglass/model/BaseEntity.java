package com.amz.spyglass.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * 基础实体类，提供审计字段
 *
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    /**
     * 记录创建时间
     */
    @Column(nullable = false, updatable = false)
    protected Instant createdAt;

    /**
     * 记录最后更新时间
     */
    @Column(nullable = false)
    protected Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}