package com.amz.spyglass.repository.alert;

import com.amz.spyglass.model.alert.ReviewAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewAlertRepository extends JpaRepository<ReviewAlert, Long> {
}
