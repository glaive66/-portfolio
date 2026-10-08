package com.autoops.mcp.hitl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalGatewayTest {

    private ApprovalGateway approvalGateway;

    @BeforeEach
    void setUp() {
        approvalGateway = new ApprovalGateway();
    }

    @Test
    @DisplayName("HITL: 위험 도구 호출 전 세션 일시 중지(SUSPEND) 등록 검증")
    void testSuspendSession() {
        // when
        PendingExecutionSession session = approvalGateway.suspend(
                "EXEC-001",
                "admin-jun",
                "enqueueBulkNotice",
                "2026년 9월 정산 오류 가맹점 50곳 대량 발송",
                List.of("SETTLE-001", "SETTLE-002")
        );

        // then
        assertThat(session).isNotNull();
        assertThat(session.getPendingId()).startsWith("PENDING-");
        assertThat(session.getStatus()).isEqualTo(ExecutionStatus.SUSPENDED);
        assertThat(approvalGateway.getAllPendingSessions()).hasSize(1);
    }

    @Test
    @DisplayName("HITL: 관리자 승인(APPROVE) 처리 및 상태 전이 검증")
    void testApproveSession() {
        // given
        PendingExecutionSession session = approvalGateway.suspend(
                "EXEC-001", "admin-jun", "createApprovalDraft", "결재 상신", null
        );

        // when
        PendingExecutionSession approved = approvalGateway.approve(
                session.getPendingId(), "super-admin", "정산오류 재처리 승인함"
        );

        // then
        assertThat(approved.getStatus()).isEqualTo(ExecutionStatus.APPROVED);
        assertThat(approved.getApproverId()).isEqualTo("super-admin");
        assertThat(approved.getApproverComment()).isEqualTo("정산오류 재처리 승인함");
    }

    @Test
    @DisplayName("HITL: 관리자 거절(REJECT) 처리 및 상태 전이 검증")
    void testRejectSession() {
        // given
        PendingExecutionSession session = approvalGateway.suspend(
                "EXEC-002", "admin-jun", "enqueueBulkNotice", "대량 발송", null
        );

        // when
        PendingExecutionSession rejected = approvalGateway.reject(
                session.getPendingId(), "super-admin", "금액 오류로 반려"
        );

        // then
        assertThat(rejected.getStatus()).isEqualTo(ExecutionStatus.REJECTED);
        assertThat(rejected.getApproverId()).isEqualTo("super-admin");
        assertThat(rejected.getApproverComment()).isEqualTo("금액 오류로 반려");
    }

    @Test
    @DisplayName("HITL: 이미 승인된 세션을 중복 처리할 경우 예외 발생")
    void testDuplicateDecisionThrowsException() {
        // given
        PendingExecutionSession session = approvalGateway.suspend(
                "EXEC-003", "admin-jun", "enqueueBulkNotice", "발송", null
        );
        approvalGateway.approve(session.getPendingId(), "admin", "승인");

        // when & then
        assertThatThrownBy(() -> approvalGateway.approve(session.getPendingId(), "admin", "재승인"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("대기 상태가 아닌 세션");
    }
}
