package com.autoops.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실시간 SSE(Server-Sent Events) 타임라인 브로드캐스터
 * AI 사고 과정, 도구 호출, 승인 대기, RabbitMQ 청크 발행 상태를 웹 UI로 실시간 전송합니다.
 */
@Slf4j
@Service
public class SseTimelineEmitterService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public record TimelineEvent(
            String executionId,
            String eventType,  // THINKING, TOOL_START, TOOL_END, SUSPENDED, APPROVED, SAGA_CHUNK, COMPLETED, ERROR
            String title,
            String detail,
            String timestamp,
            Object payload
    ) {}

    public SseEmitter connect(String executionId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10분 타임아웃

        emitters.put(executionId, emitter);
        log.info("[SSE] 클라이언트 연결 등록 - ExecutionId: {}", executionId);

        emitter.onCompletion(() -> {
            emitters.remove(executionId);
            log.info("[SSE] 연결 정상 종료 - ExecutionId: {}", executionId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(executionId);
            log.warn("[SSE] 연결 타임아웃 - ExecutionId: {}", executionId);
        });

        emitter.onError(e -> {
            emitters.remove(executionId);
            log.error("[SSE] 연결 오류 발생 - ExecutionId: {}, 사유: {}", executionId, e.getMessage());
        });

        // 초기 연결 성공 이벤트 전송
        sendEvent(executionId, "CONNECTED", "AutoOps 오케스트레이터 실시간 채널 연결됨", "대기 중...", null);

        return emitter;
    }

    public void sendEvent(String executionId, String eventType, String title, String detail, Object extra) {
        SseEmitter emitter = emitters.get(executionId);
        if (emitter == null) {
            return;
        }

        TimelineEvent event = new TimelineEvent(
                executionId,
                eventType,
                title,
                detail,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                extra
        );

        try {
            emitter.send(SseEmitter.event()
                    .name("timeline")
                    .data(event));
        } catch (IOException e) {
            emitters.remove(executionId);
            log.warn("[SSE] 전송 실패로 인한 이미터 제거 - ExecutionId: {}", executionId);
        }
    }
}
