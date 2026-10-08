package com.autoops.mcp.service;

import com.autoops.domain.audit.AiAuditLog;
import com.autoops.domain.audit.AiAuditLogRepository;
import com.autoops.mcp.tool.SettlementOpsTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrationService {

    private final ChatClient chatClient;
    private final SettlementOpsTools settlementOpsTools;
    private final AiAuditLogRepository auditLogRepository;

    /**
     * 사용자 자연어 지시에 따른 에이전트 자율 오케스트레이션 실행
     *
     * @param userId     실행 요청 관리자 ID
     * @param userPrompt 사용자 자연어 업무 지시 (예: "2026년 9월 정산 오류 가맹점 찾아서 결재 올려줘")
     * @return AI 최종 응답 및 실행 요약
     */
    public String executeOpsTask(String userId, String userPrompt) {
        String executionId = "EXEC-" + UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        log.info("[Orchestrator] 업무 지시 접수 - ExecutionId: {}, User: {}, Prompt: {}", executionId, userId, userPrompt);

        try {
            // Spring AI ChatClient에 MCP Tools 바인딩 후 자율 체이닝 실행
            String aiResponse = chatClient.prompt()
                    .user(userPrompt)
                    .tools(settlementOpsTools)
                    .call()
                    .content();

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[Orchestrator] AI 업무 수행 완료 - 소요시간: {}ms", durationMs);

            // 감사 로그 기록
            recordAuditLog(executionId, userId, userPrompt, "AutoOps-Chained-Execution",
                    "{\"prompt\":\"" + userPrompt + "\"}", "{\"response\":\"" + aiResponse + "\"}",
                    false, "COMPLETED", durationMs);

            return aiResponse;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[Orchestrator] AI 체이닝 실행 중 예외 발생 (외부 LLM 키 미설정 또는 네트워크 오류 가능) - {}", e.getMessage());

            // 비상 감사 로그 기록
            recordAuditLog(executionId, userId, userPrompt, "AutoOps-Chained-Execution-Failed",
                    "{\"error\":\"" + e.getMessage() + "\"}", null, false, "ERROR", durationMs);

            throw new RuntimeException("AI 오케스트레이션 실행 실패: " + e.getMessage(), e);
        }
    }

    private void recordAuditLog(String executionId, String userId, String prompt, String toolName,
                                String args, String result, boolean isMutation, String approvalStatus, long duration) {
        try {
            AiAuditLog auditLog = AiAuditLog.builder()
                    .executionId(executionId)
                    .userId(userId)
                    .userPrompt(prompt)
                    .toolName(toolName)
                    .toolArguments(args != null ? args : "{}")
                    .toolResult(result != null ? result : "{}")
                    .isMutation(isMutation)
                    .approvalStatus(approvalStatus)
                    .executionTimeMs(duration)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("[AuditLog] 감사 로그 저장 중 예외 발생 (무시 가능): {}", ex.getMessage());
        }
    }
}
