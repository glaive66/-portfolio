package com.autoops.domain.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "TB_AI_AUDIT_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "EXECUTION_ID", nullable = false, length = 64)
    private String executionId;

    @Column(name = "USER_ID", nullable = false, length = 50)
    private String userId;

    @Column(name = "USER_PROMPT", nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "TOOL_NAME", nullable = false, length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TOOL_ARGUMENTS", nullable = false, columnDefinition = "JSONB")
    private String toolArguments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TOOL_RESULT", columnDefinition = "JSONB")
    private String toolResult;

    @Column(name = "IS_MUTATION", nullable = false)
    private Boolean isMutation;

    @Column(name = "APPROVAL_STATUS", nullable = false, length = 20)
    private String approvalStatus;

    @Column(name = "EXECUTION_TIME_MS")
    private Long executionTimeMs;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AiAuditLog(String executionId, String userId, String userPrompt,
                      String toolName, String toolArguments, String toolResult,
                      Boolean isMutation, String approvalStatus, Long executionTimeMs) {
        this.executionId = executionId;
        this.userId = userId;
        this.userPrompt = userPrompt;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.toolResult = toolResult;
        this.isMutation = isMutation != null ? isMutation : false;
        this.approvalStatus = approvalStatus != null ? approvalStatus : "NONE";
        this.executionTimeMs = executionTimeMs;
        this.createdAt = LocalDateTime.now();
    }
}
