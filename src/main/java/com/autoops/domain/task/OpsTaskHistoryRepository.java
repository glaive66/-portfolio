package com.autoops.domain.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpsTaskHistoryRepository extends JpaRepository<OpsTaskHistory, Long> {

    Optional<OpsTaskHistory> findByExecutionId(String executionId);

    List<OpsTaskHistory> findAllByOrderByCreatedAtDesc();
}
