package com.amz.spyglass.repository;

import com.amz.spyglass.model.KeywordRankHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * V2.1 F-BIZ-001: KeywordRankHistory Repository.
 */
@Repository
public interface KeywordRankHistoryRepository extends JpaRepository<KeywordRankHistory, Long> {
}
