package com.amz.spyglass.model.alert;

import com.amz.spyglass.model.BaseEntityModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 统一告警日志实体，对应表 alert_log（已由手工 SQL 创建）。
 * 不使用枚举，所有类型、严重级别均为字符串，方便后续扩展。
 */
@Entity
@Table(name = "alert_log")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AlertLog extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asin_id", nullable = false)
    private Long asinId;

    @Column(name = "asin_code", length = 32, nullable = false)
    private String asinCode;

    @Column(name = "site", length = 16, nullable = false)
    private String site;

    @Column(name = "alert_type", length = 64, nullable = false)
    private String alertType;

    @Column(name = "severity", length = 32)
    private String severity = "INFO";

    @Column(name = "alert_at", nullable = false)
    private Instant alertAt = Instant.now();

    @Lob
    @Column(name = "old_value")
    private String oldValue;

    @Lob
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "change_percent", precision = 8, scale = 2)
    private BigDecimal changePercent;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "context_json", columnDefinition = "JSON")
    private String contextJson; // 直接存 JSON 字符串

    @Lob
    @Column(name = "message")
    private String message;
}
