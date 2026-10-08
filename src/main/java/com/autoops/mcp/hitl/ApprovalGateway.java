package com.autoops.mcp.hitl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Human-in-the-Loop (HITL) 승인 게이트웨이
 * Side-effect를 유발하는 위험 도구(MUTATION) 실행 전 세션을 일시 중지(SUSPEND)하고,
 * 관리자의 명시적 승인 후 작업을 재개(RESUME)하도록 통제합니다.
 */
@Slf4j
@Component
public class ApprovalGateway {

    private final Map<String, PendingExecutionSession> sessionStore = new ConcurrentHashMap<>();

    /**
     * 실행 중단 및 승인 대기 세션 등록 (SUSPEND)
     */
    public PendingExecutionSession suspend(String executionId, String userId, String toolName,
                                           String summary, Object payload) {
        String pendingId = "PENDING-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PendingExecutionSession session = PendingExecutionSession.builder()
                .pendingId(pendingId)
                .executionId(executionId)
                .userId(userId)
                .toolName(toolName)
                .summary(summary)
                .payload(payload)
                .status(ExecutionStatus.SUSPENDED)
                .createdAt(LocalDateTime.now())
                .build();

        sessionStore.put(pendingId, session);
        log.warn("[HITL Gateway] 위험 작업 승인 대기 발생 (SUSPENDED) - PendingId: {}, Tool: {}, 요약: {}",
                pendingId, toolName, summary);

        return session;
    }

    /**
     * 관리자 승인 처리 (RESUME 준비)
     */
    public PendingExecutionSession approve(String pendingId, String approverId, String comment) {
        PendingExecutionSession session = getSessionOrThrow(pendingId);

        if (session.getStatus() != ExecutionStatus.SUSPENDED) {
            throw new IllegalStateException("이미 처리되었거나 대기 상태가 아닌 세션입니다. (상태: " + session.getStatus() + ")");
        }

        session.approve(approverId, comment);
        log.info("[HITL Gateway] 관리자 작업 승인 완료 (APPROVED) - PendingId: {}, 승인자: {}", pendingId, approverId);

        return session;
    }

    /**
     * 관리자 거부 처리 (REJECT)
     */
    public PendingExecutionSession reject(String pendingId, String approverId, String comment) {
        PendingExecutionSession session = getSessionOrThrow(pendingId);

        if (session.getStatus() != ExecutionStatus.SUSPENDED) {
            throw new IllegalStateException("대기 상태가 아닌 세션은 거부할 수 없습니다. (상태: " + session.getStatus() + ")");
        }

        session.reject(approverId, comment);
        log.warn("[HITL Gateway] 관리자 작업 거부 처리 (REJECTED) - PendingId: {}, 거부자: {}, 사유: {}",
                pendingId, approverId, comment);

        return session;
    }

    public Optional<PendingExecutionSession> getSession(String pendingId) {
        return Optional.ofNullable(sessionStore.get(pendingId));
    }

    public List<PendingExecutionSession> getAllPendingSessions() {
        return sessionStore.values().stream()
                .filter(s -> s.getStatus() == ExecutionStatus.SUSPENDED)
                .sorted(Comparator.comparing(PendingExecutionSession::getCreatedAt).reversed())
                .toList();
    }

    private PendingExecutionSession getSessionOrThrow(String pendingId) {
        PendingExecutionSession session = sessionStore.get(pendingId);
        if (session == null) {
            throw new NoSuchElementException("승인 대기 세션을 찾을 수 없습니다. (PendingId: " + pendingId + ")");
        }
        return session;
    }
}
