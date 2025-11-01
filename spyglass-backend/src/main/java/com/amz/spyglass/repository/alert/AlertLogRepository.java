package com.amz.spyglass.repository.alert;

import com.amz.spyglass.model.alert.AlertLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {
	Page<AlertLog> findByAsinId(Long asinId, Pageable pageable);
	Page<AlertLog> findByAlertTypeIgnoreCase(String alertType, Pageable pageable);
}
