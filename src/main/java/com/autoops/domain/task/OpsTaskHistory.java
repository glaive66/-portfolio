package com.autoops.domain.task;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TB_OPS_TASK_HISTORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpsTaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TASK_ID")
    private Long taskId;

    @Column(name = "EXECUTION_ID", nullable = false, length = 64, unique = true)
    private String executionId;

    @Column(name = "USER_ID", nullable = false, length = 50)
    private String userId;

    @Column(name = "PROMPT", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status; // RUNNING, SUSPENDED, COMPLETED, REJECTED, FAILED

    @Column(name = "DRAFT_ID", length = 32)
    private String draftId;

    @Column(name = "TARGET_MERCHANT_COUNT")
    private Integer targetMerchantCount;

    @Column(name = "TOTAL_PENDING_AMOUNT", precision = 15, scale = 2)
    private BigDecimal totalPendingAmount;

    @Column(name = "PUBLISHED_CHUNKS")
    private Integer publishedChunks;

    @Column(name = "APPROVER_ID", length = 50)
    private String approverId;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Builder
    public OpsTaskHistory(String executionId, String userId, String prompt, String status) {
        this.executionId = executionId;
        this.userId = userId;
        this.prompt = prompt;
        this.status = status != null ? status : "RUNNING";
        this.createdAt = LocalDateTime.now();
    }

    public void updateSuspended(int merchantCount, BigDecimal totalAmount) {
        this.status = "SUSPENDED";
        this.targetMerchantCount = merchantCount;
        this.totalPendingAmount = totalAmount;
    }

    public void completeSuccess(String draftId, int publishedChunks, String approverId) {
        this.status = "COMPLETED";
        this.draftId = draftId;
        this.publishedChunks = publishedChunks;
        this.approverId = approverId;
        this.completedAt = LocalDateTime.now();
    }

    public void reject(String approverId) {
        this.status = "REJECTED";
        this.approverId = approverId;
        this.completedAt = LocalDateTime.now();
    }
}
