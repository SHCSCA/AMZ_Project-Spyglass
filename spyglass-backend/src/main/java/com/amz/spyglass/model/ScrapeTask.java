package com.amz.spyglass.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scrape_tasks")
public class ScrapeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long asinId;

    private String status; // PENDING, RUNNING, SUCCESS, FAILED

    private Instant runAt = Instant.now();

    private String message;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAsinId() { return asinId; }
    public void setAsinId(Long asinId) { this.asinId = asinId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant runAt) { this.runAt = runAt; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
