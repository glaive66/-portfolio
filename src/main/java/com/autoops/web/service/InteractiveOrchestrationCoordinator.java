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
    private final com.autoops.domain.task.OpsTaskHistoryRepository taskHistoryRepository;

    public record QueryResultPayload(
            String taskType, // "AGGREGATION", "QUERY", "MUTATION"
            String settlementYm,
            int count,
            BigDecimal totalAmount,
            List<SettlementQueryDto.Item> items
    ) {}

    /**
     * 1단계: 사용자 자연어 지시 접수 및 자율 탐색 시작 (비동기)
     */
    public String startOpsTask(String userId, String userPrompt) {
        String executionId = "EXEC-" + UUID.randomUUID().toString().substring(0, 8);

        // 작업 이력 등록 (RUNNING)
        com.autoops.domain.task.OpsTaskHistory task = com.autoops.domain.task.OpsTaskHistory.builder()
                .executionId(executionId)
                .userId(userId)
                .prompt(userPrompt)
                .status("RUNNING")
                .build();
        taskHistoryRepository.save(task);

        CompletableFuture.runAsync(() -> processTaskPipeline(executionId, userId, userPrompt));

        return executionId;
    }

    private void processTaskPipeline(String executionId, String userId, String userPrompt) {
        long startTime = System.currentTimeMillis();

        try {
            String ym = parseSettlementYm(userPrompt);
            int limit = parseLimit(userPrompt);
            boolean isMutation = isMutationIntent(userPrompt);
            boolean isSum = isAggregationIntent(userPrompt);

            // Step 1: AI 계획 수립 (Reasoning)
            sleep(400);
            if (isSum) {
                sseService.sendEvent(executionId, "THINKING", "AI 오케스트레이터 계획 수립",
                        String.format("자연어 의도 분석: '정산 오류 금액 집계(SUM)' 감지 -> 대상 연월: %s, 최근 건수: %d건. [단순 조회/집계(READ)로 부수 효과 없음]", ym, limit), null);
            } else if (!isMutation) {
                sseService.sendEvent(executionId, "THINKING", "AI 오케스트레이터 계획 수립",
                        String.format("자연어 의도 분석: '정산 현황 단순 조회(QUERY)' 감지 -> 대상 연월: %s, 건수: %d건. [단순 조회(READ)로 부수 효과 없음]", ym, limit), null);
            } else {
                sseService.sendEvent(executionId, "THINKING", "AI 오케스트레이터 계획 수립",
                        String.format("자연어 의도 분석: '정산 오류 일괄 조치 & 결재 상신(MUTATION)' 감지 -> 대량 발송/상신 전 관리자 승인(HITL) 필수", ym), null);
            }

            // Step 2: READ 도구 실행 (정산 오류 조회)
            sleep(600);
            sseService.sendEvent(executionId, "TOOL_START", "도구 실행: findSettlementErrors",
                    String.format("정산 연월: %s 대상 가맹점 데이터 조회 시작 (최대 %d건)...", ym, limit), null);

            SettlementQueryDto.Response queryResult = settlementOpsTools.findSettlementErrors(
                    new SettlementQueryDto.Request(ym, limit)
            );

            // 실제 limit 개수만큼 슬라이싱
            List<SettlementQueryDto.Item> targetItems = queryResult.errorList().stream()
                    .limit(limit)
                    .toList();

            BigDecimal totalSum = targetItems.stream()
                    .map(SettlementQueryDto.Item::pendingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            sseService.sendEvent(executionId, "TOOL_END", "도구 완료: findSettlementErrors",
                    String.format("조건에 부합하는 오류 가맹점 %d건 조회 완료 (합산 대상: %d건, 총 %,d 원)",
                            queryResult.totalErrorCount(), targetItems.size(), totalSum.longValue()), null);

            if (targetItems.isEmpty()) {
                sseService.sendEvent(executionId, "COMPLETED", "조회 완료", "해당 조건에 처리할 정산 오류 데이터가 없습니다.", null);
                taskHistoryRepository.findByExecutionId(executionId).ifPresent(th -> {
                    th.completeReadTask(0, BigDecimal.ZERO);
                    taskHistoryRepository.save(th);
                });
                return;
            }

            // [분기 A] 금액 합산/집계(SUM) 의도인 경우 -> 즉시 결과 계산 및 완료 (승인 불필요!)
            if (isSum) {
                sleep(500);
                StringBuilder detailBuilder = new StringBuilder();
                detailBuilder.append(String.format("📌 [%s 정산오류 최근 %d건 금액 집계 결과]\n", ym, targetItems.size()));
                detailBuilder.append(String.format("• 집계 대상: 총 %d개 가맹점\n", targetItems.size()));
                detailBuilder.append(String.format("• 총 합계 금액: %,d 원\n\n", totalSum.longValue()));
                detailBuilder.append("📋 [상세 가맹점 내역]\n");
                for (int i = 0; i < Math.min(5, targetItems.size()); i++) {
                    SettlementQueryDto.Item it = targetItems.get(i);
                    detailBuilder.append(String.format("%d. %s : %,d원 (%s)\n",
                            i + 1, it.merchantName(), it.pendingAmount().longValue(), it.failReason()));
                }
                if (targetItems.size() > 5) {
                    detailBuilder.append(String.format("... 외 %d개 가맹점\n", targetItems.size() - 5));
                }
                detailBuilder.append("\n※ 본 작업은 단순 집계/조회(READ) 업무이므로 부수 효과(Side-effect)가 없어 관리자 승인(HITL) 절차 없이 즉시 처리 완료되었습니다.");

                QueryResultPayload payload = new QueryResultPayload("AGGREGATION", ym, targetItems.size(), totalSum, targetItems);

                sseService.sendEvent(executionId, "COMPLETED",
                        String.format("📊 금액 합산 완료: 총 %,d 원 (최근 %d건)", totalSum.longValue(), targetItems.size()),
                        detailBuilder.toString(), payload);

                taskHistoryRepository.findByExecutionId(executionId).ifPresent(th -> {
                    th.completeReadTask(targetItems.size(), totalSum);
                    taskHistoryRepository.save(th);
                });
                return;
            }

            // [분기 B] 단순 조회(QUERY_ONLY) 의도인 경우 -> 즉시 완료 (승인 불필요!)
            if (!isMutation) {
                sleep(500);
                String detail = String.format("정산 연월 %s 대상 오류 가맹점 %d건 조회가 완료되었습니다.\n총 보류 금액: %,d 원\n\n※ 단순 조회 업무이므로 부수 효과 없이 완료되었습니다.",
                        ym, targetItems.size(), totalSum.longValue());

                QueryResultPayload payload = new QueryResultPayload("QUERY", ym, targetItems.size(), totalSum, targetItems);

                sseService.sendEvent(executionId, "COMPLETED", "🔍 오류 가맹점 현황 조회 완료", detail, payload);

                taskHistoryRepository.findByExecutionId(executionId).ifPresent(th -> {
                    th.completeReadTask(targetItems.size(), totalSum);
                    taskHistoryRepository.save(th);
                });
                return;
            }

            // [분기 C] 대량 조치 / 결재 상신(MUTATION) 의도인 경우 -> 안내문 생성 후 HITL 승인 게이트웨이 호출!
            sleep(500);
            SettlementQueryDto.Item firstItem = targetItems.get(0);
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
                    targetItems.size(), totalSum.longValue());

            SettlementQueryDto.Response scopedResponse = new SettlementQueryDto.Response(
                    ym, targetItems.size(), totalSum, targetItems
            );

            PendingExecutionSession session = approvalGateway.suspend(
                    executionId, userId, "executeNoticePipeline", summary, scopedResponse
            );

            // 작업 이력 상태 갱신 (SUSPENDED)
            taskHistoryRepository.findByExecutionId(executionId).ifPresent(th -> {
                th.updateSuspended(targetItems.size(), totalSum);
                taskHistoryRepository.save(th);
            });

            sseService.sendEvent(executionId, "SUSPENDED", "⚠️ [HITL] 관리자 승인 대기 중 (작업 일시 중지)",
                    "부수 효과(Side-effect) 방지를 위해 관리자 검토 대기: " + summary + " [토큰: " + session.getPendingId() + "]", session);

        } catch (Exception e) {
            log.error("[Coordinator] 파이프라인 에러 - {}", e.getMessage(), e);
            sseService.sendEvent(executionId, "ERROR", "오케스트레이션 실행 오류", e.getMessage(), null);
        }
    }

    private String parseSettlementYm(String prompt) {
        if (prompt == null) return "202609";
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(20\\d{2})(0[1-9]|1[0-2])").matcher(prompt);
        if (m1.find()) return m1.group();

        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("([1-9]|1[0-2])월").matcher(prompt);
        if (m2.find()) {
            int month = Integer.parseInt(m2.group(1));
            return String.format("2026%02d", month);
        }
        return "202609";
    }

    private int parseLimit(String prompt) {
        if (prompt == null) return 50;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:최근|상위)?\\s*(\\d+)\\s*(?:건|개|곳)").matcher(prompt);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 50;
    }

    private boolean isMutationIntent(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("결재") || lower.contains("상신") || lower.contains("기안")
                || lower.contains("발송") || lower.contains("보내") || lower.contains("전송")
                || lower.contains("통보") || lower.contains("조치") || lower.contains("소명 안내문 작성");
    }

    private boolean isAggregationIntent(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("합") || lower.contains("더해") || lower.contains("합계")
                || lower.contains("총액") || lower.contains("총 금액") || lower.contains("총합")
                || lower.contains("얼마") || lower.contains("계산");
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
            // 작업 이력 상태 갱신 (COMPLETED)
            taskHistoryRepository.findByExecutionId(executionId).ifPresent(th -> {
                th.completeSuccess(sagaResult.getDraftId(), sagaResult.getPublishedChunks(), approverId);
                taskHistoryRepository.save(th);
            });

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

        // 작업 이력 상태 갱신 (REJECTED)
        taskHistoryRepository.findByExecutionId(executionId).ifPresent(th -> {
            th.reject(approverId);
            taskHistoryRepository.save(th);
        });

        sseService.sendEvent(executionId, "REJECTED", "❌ 관리자 작업 거부 (작업 취소됨)",
                "거부자: " + approverId + ", 반려 사유: " + comment, null);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
