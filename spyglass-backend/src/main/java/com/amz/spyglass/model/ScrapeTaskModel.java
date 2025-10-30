package com.amz.spyglass.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * 抓取任务实体
 * 用于记录和管理商品页面的抓取任务，包括状态跟踪和重试机制
 * 
 * @author AI
 * @version 1.0.0
 * @since 2025-10-29
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scrape_task")
public class ScrapeTaskModel extends BaseEntityModel {

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,    // 等待执行
        RUNNING,    // 执行中
        SUCCESS,    // 执行成功
        FAILED      // 执行失败
    }

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的ASIN ID，不可为空
     */
    @Column(nullable = false)
    private Long asinId;

    /**
     * 任务状态，默认为等待执行
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * 执行结果消息，可以包含错误信息或执行日志
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * 重试次数，默认为0，每次重试时递增
     */
    @Column(nullable = false)
    private Integer retryCount = 0;

    /**
     * 计划执行时间，由调度器设置
     */
    @Column(nullable = false)
    private Instant runAt = Instant.now();
}
