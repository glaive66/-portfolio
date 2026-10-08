package com.autoops.web.service;

import com.autoops.domain.audit.AiAuditLog;
import com.autoops.domain.audit.AiAuditLogRepository;
import com.autoops.infrastructure.rabbitmq.dto.NoticeChunkMessage;
import com.autoops.mcp.hitl.ApprovalGateway;
import com.autoops.mcp.hitl.PendingExecutionSession;
import com.autoops.mcp.saga.SagaOrchestrator;
import com.autoops.mcp.tool.SettlementOpsTools;
import com.autoops.mcp.tool.dto.NoticeTemplateDto;
import com.autoops.mcp.tool.dto.SettlementQueryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 실시간 웹 운영 콘솔과 백엔드 MCP 에이전트를 연결하는 코디네이터
 * SSE 스트리밍과 Human-in-the-Loop 승인 라이프사이클을 총괄합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveOrchestrationCoordinator {

    private final SettlementOpsTools settlementOpsTools;
    private final ApprovalGateway approvalGateway;
    private final SagaOrchestrator sagaOrchestrator;
    private final SseTimelineEmitterService sseService;
    private final AiAuditLogRepository auditLogRepository;

    /**
     * 1단계: 사용자 자연어 지시 접수 및 자율 탐색 시작 (비동기)
     */
    public String startOpsTask(String userId, String userPrompt) {
        String executionId = "EXEC-" + UUID.randomUUID().toString().substring(0, 8);

        CompletableFuture.runAsync(() -> processTaskPipeline(executionId, userId, userPrompt));

        return executionId;
    }

    private void processTaskPipeline(String executionId, String userId, String userPrompt) {
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: AI 계획 수립 (Reasoning)
            sleep(400);
            sseService.sendEvent(executionId, "THINKING", "AI 오케스트레이터 계획 수립",
                    "자연어 분석: '" + userPrompt + "' -> 정산 오류 조회 도구(findSettlementErrors) 실행 결정", null);

            // Step 2: READ 도구 1 실행 (정산 오류 조회)
            sleep(600);
            sseService.sendEvent(executionId, "TOOL_START", "도구 실행: findSettlementErrors",
                    "정산 연월: 202609 대상 가맹점 데이터 조회 시작...", null);

            SettlementQueryDto.Response queryResult = settlementOpsTools.findSettlementErrors(
                    new SettlementQueryDto.Request("202609", 50)
            );

            sseService.sendEvent(executionId, "TOOL_END", "도구 완료: findSettlementErrors",
                    String.format("오류 가맹점 %d건 발견, 총 보류금액: %,d 원",
                            queryResult.totalErrorCount(), queryResult.totalPendingAmount().longValue()), null);

            if (queryResult.errorList().isEmpty()) {
                sseService.sendEvent(executionId, "COMPLETED", "조회 완료", "해당 연월에 처리할 정산 오류가 없습니다.", null);
                return;
            }

            // Step 3: READ 도구 2 실행 (맞춤 안내문 템플릿 실시간 생성)
            sleep(500);
            SettlementQueryDto.Item firstItem = queryResult.errorList().get(0);
            sseService.sendEvent(executionId, "TOOL_START", "도구 실행: generateNoticeTemplate",
                    "대표 가맹점(" + firstItem.merchantName() + ") 기준 B2B 소명 요청 템플릿 생성 중...", null);

            NoticeTemplateDto.Response templateResult = settlementOpsTools.generateNoticeTemplate(
                    new NoticeTemplateDto.Request(
                            firstItem.settlementId(),
                            firstItem.merchantName(),
                            firstItem.failReason(),
                            firstItem.pendingAmount()
                    )
            );

            sseService.sendEvent(executionId, "TOOL_END", "도구 완료: generateNoticeTemplate",
                    "템플릿 제목: " + templateResult.emailSubject(), null);

            // Step 4: Human-in-the-Loop 승인 게이트웨이 진입 (MUTATION 위험 작업 전 일시 중지)
            sleep(500);
            String summary = String.format("정산 오류 가맹점 %d곳 대상 대량 알림 발송 및 결재 상신 (총 %,d 원)",
                    queryResult.totalErrorCount(), queryResult.totalPendingAmount().longValue());

            PendingExecutionSession session = approvalGateway.suspend(
                    executionId, userId, "executeNoticePipeline", summary, queryResult
            );

            sseService.sendEvent(executionId, "SUSPENDED", "⚠️ [HITL] 관리자 승인 대기 중 (작업 일시 중지)",
                    "부수 효과(Side-effect) 방지를 위해 관리자 검토 대기: " + summary + " [토큰: " + session.getPendingId() + "]", session);

        } catch (Exception e) {
            log.error("[Coordinator] 파이프라인 에러 - {}", e.getMessage(), e);
            sseService.sendEvent(executionId, "ERROR", "오케스트레이션 실행 오류", e.getMessage(), null);
        }
    }

    /**
     * 2단계: 관리자가 웹 콘솔에서 승인(APPROVE) 클릭 시 파이프라인 재개 (RESUME)
     */
    public SagaOrchestrator.SagaResult resumeWithApproval(String pendingId, String approverId, String comment) {
        PendingExecutionSession session = approvalGateway.approve(pendingId, approverId, comment);
        String executionId = session.getExecutionId();

        sseService.sendEvent(executionId, "APPROVED", "✅ 관리자 승인 확인됨 (작업 재개)",
                "승인자: " + approverId + ", 의견: " + comment, null);

        // 페이로드에서 아이템 추출
        SettlementQueryDto.Response queryResult = (SettlementQueryDto.Response) session.getPayload();
        List<NoticeChunkMessage.NoticeItem> noticeItems = queryResult.errorList().stream()
                .map(item -> new NoticeChunkMessage.NoticeItem(
                        item.settlementId(),
                        item.merchantName(),
                        item.pendingAmount(),
                        item.failReason()
                ))
                .toList();

        // Step 5: Saga 분산 트랜잭션 실행 (전자결재 기안 상신 + RabbitMQ 10건 단위 Chunk 분할 발행)
        sseService.sendEvent(executionId, "SAGA_CHUNK", "Saga 트랜잭션 시작 (RabbitMQ 10건 단위 분할 발행)",
                String.format("총 %d개 가맹점을 10건씩 분할하여 비동기 메시지 브로커로 전송합니다.", noticeItems.size()), null);

        SagaOrchestrator.SagaResult sagaResult = sagaOrchestrator.executeNoticePipeline(
                executionId,
                noticeItems,
                "2026년 9월 정산 오류 가맹점 일괄 소명 안내 및 조치 건",
                "상세 오류 내역에 따른 일괄 소명 요청",
                10 // 청크 사이즈 10건
        );

        if (sagaResult.isSuccess()) {
            sseService.sendEvent(executionId, "COMPLETED", "🎉 AutoOps 전체 자율 오케스트레이션 성공 완료",
                    String.format("결재 기안(%s) 등록 및 RabbitMQ 총 %d개 청크 분할 발행 완료!",
                            sagaResult.getDraftId(), sagaResult.getPublishedChunks()), sagaResult);
        } else {
            sseService.sendEvent(executionId, "ERROR", "🚨 Saga 실행 실패 및 보상 트랜잭션 롤백 완료",
                    sagaResult.getMessage(), sagaResult);
        }

        return sagaResult;
    }

    /**
     * 관리자가 거절(REJECT) 클릭 시
     */
    public void resumeWithReject(String pendingId, String approverId, String comment) {
        PendingExecutionSession session = approvalGateway.reject(pendingId, approverId, comment);
        String executionId = session.getExecutionId();

        sseService.sendEvent(executionId, "REJECTED", "❌ 관리자 작업 거부 (작업 취소됨)",
                "거부자: " + approverId + ", 반려 사유: " + comment, null);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
