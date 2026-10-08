package com.autoops.mcp.saga;

import com.autoops.domain.approval.ApprovalDraft;
import com.autoops.domain.approval.ApprovalDraftRepository;
import com.autoops.domain.approval.ApprovalStatus;
import com.autoops.domain.audit.AiAuditLog;
import com.autoops.domain.audit.AiAuditLogRepository;
import com.autoops.infrastructure.rabbitmq.NoticeChunkProducer;
import com.autoops.infrastructure.rabbitmq.dto.NoticeChunkMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Saga 패턴 분산 트랜잭션 오케스트레이터
 * 다단계 작업(결재 상신 -> RabbitMQ Chunk 분할 발행) 중 실패 발생 시
 * 선행 완료된 단계에 대해 보상 트랜잭션(Compensating Transaction)을 자동 실행하여 정합성을 보장합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final ApprovalDraftRepository approvalDraftRepository;
    private final NoticeChunkProducer chunkProducer;
    private final AiAuditLogRepository auditLogRepository;

    @Getter
    @Builder
    public static class SagaResult {
        private final String sagaId;
        private final String draftId;
        private final int publishedChunks;
        private final boolean success;
        private final boolean compensated;
        private final String message;
    }

    /**
     * 정산 오류 안내문 발송 & 결재 상신 분산 파이프라인 (Saga)
     */
    @Transactional
    public SagaResult executeNoticePipeline(String executionId,
                                            List<NoticeChunkMessage.NoticeItem> items,
                                            String noticeTitle,
                                            String noticeBody,
                                            int chunkSize) {
        String sagaId = "SAGA-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[Saga Orchestrator] Saga 트랜잭션 시작 (SagaId: {}, ExecutionId: {})", sagaId, executionId);

        ApprovalDraft draft = null;
        try {
            // Step 1: 전자결재 상신 기안 생성
            String draftId = "DRAFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            BigDecimal totalAmount = items.stream()
                    .map(NoticeChunkMessage.NoticeItem::pendingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            draft = ApprovalDraft.builder()
                    .draftId(draftId)
                    .title("[자동상신] " + noticeTitle)
                    .content(String.format("총 %d개 가맹점 안내문 발송 승인 건 (총액: %s원)\n\n%s",
                            items.size(), totalAmount, noticeBody))
                    .status(ApprovalStatus.DRAFTED)
                    .createdByAi(true)
                    .build();

            approvalDraftRepository.save(draft);
            log.info("[Saga Orchestrator] Step 1 성공: 전자결재 기안 등록 완료 (DraftId: {})", draftId);

            // Step 2: RabbitMQ Chunk 분할 발행 (10건 단위)
            int publishedChunks = chunkProducer.sendNoticeChunks(
                    executionId, items, noticeTitle, noticeBody, chunkSize
            );
            log.info("[Saga Orchestrator] Step 2 성공: 총 {}개 청크 MQ 발행 완료", publishedChunks);

            return SagaResult.builder()
                    .sagaId(sagaId)
                    .draftId(draftId)
                    .publishedChunks(publishedChunks)
                    .success(true)
                    .compensated(false)
                    .message("Saga 파이프라인 정상 완료 (결재 기안 상신 및 비동기 청크 발행 성공)")
                    .build();

        } catch (Exception ex) {
            log.error("[Saga Orchestrator] Saga 단계 실행 중 장애 발생 - 원인: {}", ex.getMessage());

            // 보상 트랜잭션(Compensating Transaction) 발동: Step 1 결재 기안 롤백(REJECTED)
            boolean compensated = false;
            if (draft != null) {
                log.warn("[Saga Orchestrator] 🚨 보상 트랜잭션 발동: 기안({}) 상태를 취소(REJECTED)로 롤백합니다.", draft.getDraftId());
                draft.reject("Saga 장애 복구: RabbitMQ 청크 발행 실패로 인한 자동 보상 롤백");
                approvalDraftRepository.save(draft);
                compensated = true;
            }

            // 장애 감사 로그 기록
            recordSagaFailureAudit(executionId, sagaId, ex.getMessage(), compensated);

            return SagaResult.builder()
                    .sagaId(sagaId)
                    .draftId(draft != null ? draft.getDraftId() : null)
                    .publishedChunks(0)
                    .success(false)
                    .compensated(compensated)
                    .message("Saga 파이프라인 실패 및 보상 트랜잭션 수행 완료: " + ex.getMessage())
                    .build();
        }
    }

    private void recordSagaFailureAudit(String executionId, String sagaId, String error, boolean compensated) {
        try {
            AiAuditLog auditLog = AiAuditLog.builder()
                    .executionId(executionId)
                    .userId("SYSTEM-SAGA")
                    .userPrompt("SAGA-EXECUTION-" + sagaId)
                    .toolName("SagaNoticePipeline")
                    .toolArguments("{\"sagaId\":\"" + sagaId + "\"}")
                    .toolResult("{\"error\":\"" + error + "\",\"compensated\":" + compensated + "}")
                    .isMutation(true)
                    .approvalStatus("COMPENSATED")
                    .executionTimeMs(0L)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("[Saga Orchestrator] 감사 로그 저장 실패: {}", e.getMessage());
        }
    }
}
