package com.amz.spyglass.model.alert;

import com.amz.spyglass.model.BaseEntityModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "review_alert")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReviewAlert extends BaseEntityModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asin_id", nullable = false)
    private Long asinId;

    @Column(name = "review_id", nullable = false, unique = true, length = 128)
    private String reviewId;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Lob
    @Column(name = "review_text")
    private String reviewText;

    @Column(name = "alert_at", nullable = false)
    private Instant alertAt = Instant.now();

    public ReviewAlert(Long asinId, String reviewId, Integer rating, LocalDate reviewDate, String reviewText) {
        this.asinId = asinId;
        this.reviewId = reviewId;
        this.rating = rating;
        this.reviewDate = reviewDate;
        this.reviewText = reviewText;
    }
}
