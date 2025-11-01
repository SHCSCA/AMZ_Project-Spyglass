package com.amz.spyglass.repository.alert;

import com.amz.spyglass.model.alert.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {
}
