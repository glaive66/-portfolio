package com.autoops.mcp.hitl;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PendingExecutionSession {

    private final String pendingId;
    private final String executionId;
    private final String userId;
    private final String toolName;
    private final String summary;
    private final Object payload;
    private ExecutionStatus status;
    private String approverId;
    private String approverComment;
    private final LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    public void approve(String approverId, String comment) {
        this.status = ExecutionStatus.APPROVED;
        this.approverId = approverId;
        this.approverComment = comment;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(String approverId, String comment) {
        this.status = ExecutionStatus.REJECTED;
        this.approverId = approverId;
        this.approverComment = comment;
        this.decidedAt = LocalDateTime.now();
    }
}
