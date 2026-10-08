package com.autoops.domain.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalDraftRepository extends JpaRepository<ApprovalDraft, String> {

    List<ApprovalDraft> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalDraft> findAllByOrderByCreatedAtDesc();
}
