package com.amz.spyglass.model.alert;

import com.amz.spyglass.model.BaseEntityModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "change_alert")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ChangeAlert extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asin_id", nullable = false)
    private Long asinId;

    @Column(name = "alert_type", nullable = false, length = 64)
    private String alertType;

    @Lob
    @Column(name = "old_value")
    private String oldValue;

    @Lob
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "alert_at", nullable = false)
    private Instant alertAt = Instant.now();

    public ChangeAlert(Long asinId, String alertType, String oldValue, String newValue) {
        this.asinId = asinId;
        this.alertType = alertType;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}
