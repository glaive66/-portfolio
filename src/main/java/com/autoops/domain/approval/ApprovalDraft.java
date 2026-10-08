package com.autoops.domain.approval;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_APPROVAL_DRAFT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalDraft {

    @Id
    @Column(name = "DRAFT_ID", length = 32)
    private String draftId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(name = "CREATED_BY_AI")
    private Boolean createdByAi;

    @Column(name = "APPROVAL_COMMENT", length = 500)
    private String approvalComment;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    @Builder
    public ApprovalDraft(String draftId, String title, String content,
                         ApprovalStatus status, Boolean createdByAi) {
        this.draftId = draftId;
        this.title = title;
        this.content = content;
        this.status = status != null ? status : ApprovalStatus.DRAFTED;
        this.createdByAi = createdByAi != null ? createdByAi : true;
        this.createdAt = LocalDateTime.now();
    }

    public void approve(String comment) {
        this.status = ApprovalStatus.APPROVED;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(String comment) {
        this.status = ApprovalStatus.REJECTED;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }
}
