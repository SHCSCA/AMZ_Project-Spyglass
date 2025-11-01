package com.amz.spyglass.repository.alert;

import com.amz.spyglass.model.alert.ChangeAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChangeAlertRepository extends JpaRepository<ChangeAlert, Long> {
}
