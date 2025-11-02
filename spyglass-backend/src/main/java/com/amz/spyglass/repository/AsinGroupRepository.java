package com.amz.spyglass.repository;

import com.amz.spyglass.model.AsinGroupModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AsinGroupRepository extends JpaRepository<AsinGroupModel, Long> {
}
