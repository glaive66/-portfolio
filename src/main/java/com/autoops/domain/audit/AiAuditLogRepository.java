package com.autoops.domain.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {

    List<AiAuditLog> findByExecutionIdOrderByCreatedAtAsc(String executionId);

    List<AiAuditLog> findAllByOrderByCreatedAtDesc();
}
