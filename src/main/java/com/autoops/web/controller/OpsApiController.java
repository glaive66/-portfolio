package com.autoops.web.controller;

import com.autoops.domain.approval.ApprovalDraft;
import com.autoops.domain.approval.ApprovalDraftRepository;
import com.autoops.domain.audit.AiAuditLog;
import com.autoops.domain.audit.AiAuditLogRepository;
import com.autoops.domain.settlement.MerchantSettlement;
import com.autoops.domain.settlement.MerchantSettlementRepository;
import com.autoops.domain.task.OpsTaskHistory;
import com.autoops.domain.task.OpsTaskHistoryRepository;
import com.autoops.mcp.hitl.ApprovalGateway;
import com.autoops.mcp.hitl.PendingExecutionSession;
import com.autoops.mcp.saga.SagaOrchestrator;
import com.autoops.web.service.InteractiveOrchestrationCoordinator;
import com.autoops.web.service.SseTimelineEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsApiController {

    private final InteractiveOrchestrationCoordinator coordinator;
    private final SseTimelineEmitterService sseService;
    private final ApprovalGateway approvalGateway;
    private final MerchantSettlementRepository settlementRepository;
    private final ApprovalDraftRepository approvalDraftRepository;
    private final AiAuditLogRepository auditLogRepository;
    private final OpsTaskHistoryRepository taskHistoryRepository;
    private final com.autoops.infrastructure.rabbitmq.NoticeChunkConsumer chunkConsumer;

    public record CommandRequest(String userId, String prompt) {}
    public record DecisionRequest(String approverId, String comment) {}

    /**
     * 1. 자연어 업무 명령 접수
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, String>> submitCommand(@RequestBody CommandRequest request) {
        String userId = (request.userId() != null && !request.userId().isBlank()) ? request.userId() : "admin-user";
        String executionId = coordinator.startOpsTask(userId, request.prompt());
        return ResponseEntity.ok(Map.of("executionId", executionId, "status", "ACCEPTED"));
    }

    /**
     * 2. SSE 실시간 스트리밍 채널 구독
     */
    @GetMapping(value = "/stream/{executionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeStream(@PathVariable String executionId) {
        return sseService.connect(executionId);
    }

    /**
     * 3. 현재 승인 대기 목록 조회
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingExecutionSession>> getPendingSessions() {
        return ResponseEntity.ok(approvalGateway.getAllPendingSessions());
    }

    /**
     * 4. 관리자 승인 (RESUME)
     */
    @PostMapping("/pending/{pendingId}/approve")
    public ResponseEntity<SagaOrchestrator.SagaResult> approveTask(
            @PathVariable String pendingId,
            @RequestBody(required = false) DecisionRequest request) {
        String approver = (request != null && request.approverId() != null) ? request.approverId() : "ops-lead";
        String comment = (request != null && request.comment() != null) ? request.comment() : "정산오류 일괄조치 승인";

        SagaOrchestrator.SagaResult result = coordinator.resumeWithApproval(pendingId, approver, comment);
        return ResponseEntity.ok(result);
    }

    /**
     * 5. 관리자 거부 (REJECT)
     */
    @PostMapping("/pending/{pendingId}/reject")
    public ResponseEntity<Map<String, String>> rejectTask(
            @PathVariable String pendingId,
            @RequestBody(required = false) DecisionRequest request) {
        String approver = (request != null && request.approverId() != null) ? request.approverId() : "ops-lead";
        String comment = (request != null && request.comment() != null) ? request.comment() : "관리자 수동 반려";

        coordinator.resumeWithReject(pendingId, approver, comment);
        return ResponseEntity.ok(Map.of("status", "REJECTED", "pendingId", pendingId));
    }

    /**
     * 6. 정산 데이터 현황 조회
     */
    @GetMapping("/settlements")
    public ResponseEntity<List<MerchantSettlement>> getSettlements() {
        return ResponseEntity.ok(settlementRepository.findBySettlementYm("202609"));
    }

    /**
     * 7. 전자결재 상신 내역 조회
     */
    @GetMapping("/drafts")
    public ResponseEntity<List<ApprovalDraft>> getDrafts() {
        return ResponseEntity.ok(approvalDraftRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * 7-1. 전자결재 기안 단건 상세 조회 (품의서 뷰)
     */
    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<ApprovalDraft> getDraftDetail(@PathVariable String draftId) {
        return approvalDraftRepository.findById(draftId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 8. AI 감사 로그 전수 조회
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AiAuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * 9. RabbitMQ Chunk 분할 비동기 처리 이력 조회
     */
     @GetMapping("/chunks")
     public ResponseEntity<List<com.autoops.infrastructure.rabbitmq.NoticeChunkConsumer.ChunkReceiptRecord>> getChunks() {
         return ResponseEntity.ok(chunkConsumer.getProcessedChunks());
     }

    /**
     * 10. AI 오케스트레이션 작업 이력 게시판 목록 조회 (최신순)
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<OpsTaskHistory>> getTasks() {
        return ResponseEntity.ok(taskHistoryRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * 10-1. AI 오케스트레이션 작업 단건 상세 조회
     */
    @GetMapping("/tasks/{executionId}")
    public ResponseEntity<OpsTaskHistory> getTaskDetail(@PathVariable String executionId) {
        return taskHistoryRepository.findByExecutionId(executionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
