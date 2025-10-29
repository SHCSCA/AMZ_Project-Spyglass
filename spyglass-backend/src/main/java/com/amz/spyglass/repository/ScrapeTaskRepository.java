package com.amz.spyglass.repository;

import com.amz.spyglass.model.ScrapeTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeTaskRepository extends JpaRepository<ScrapeTask, Long> {
}
