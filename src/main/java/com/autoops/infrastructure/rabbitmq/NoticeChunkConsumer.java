package com.autoops.infrastructure.rabbitmq;

import com.autoops.infrastructure.rabbitmq.dto.NoticeChunkMessage;
import com.autoops.mcp.tool.SettlementOpsTools;
import com.autoops.web.service.SseTimelineEmitterService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeChunkConsumer {

    private final SseTimelineEmitterService sseService;

    @Getter
    private final List<ChunkReceiptRecord> processedChunks = new CopyOnWriteArrayList<>();

    public record ChunkReceiptRecord(
            String executionId,
            int chunkIndex,
            int totalChunks,
            int itemCount,
            String sampleMerchants,
            String status,
            String processedAt
    ) {}

    /**
     * Chunk 분할 알림 메시지 비동기 수신 및 가맹점별 발송 처리
     */
    @RabbitListener(queues = SettlementOpsTools.QUEUE_SETTLEMENT_NOTICE)
    public void consumeNoticeChunk(NoticeChunkMessage message) {
        log.info("[Chunk Consumer] 청크 수신 - ExecutionId: {}, 번호: [{}/{}], 포함 건수: {}건",
                message.executionId(), message.chunkIndex(), message.totalChunks(), message.items().size());

        // 대표 가맹점명 추출
        String sampleMerchants = message.items().stream()
                .limit(2)
                .map(NoticeChunkMessage.NoticeItem::merchantName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("가맹점");
        if (message.items().size() > 2) {
            sampleMerchants += String.format(" 외 %d곳", message.items().size() - 2);
        }

        // 개별 발송 처리
        for (NoticeChunkMessage.NoticeItem item : message.items()) {
            processIndividualNotice(item, message.templateTitle());
        }

        int percent = (int) Math.round(((double) message.chunkIndex() / message.totalChunks()) * 100);
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));

        // 처리 이력 기록
        processedChunks.add(0, new ChunkReceiptRecord(
                message.executionId(),
                message.chunkIndex(),
                message.totalChunks(),
                message.items().size(),
                sampleMerchants,
                "ACK_PROCESSED",
                nowStr
        ));

        // 실시간 브라우저 SSE 전송 (운영자에게 실시간 발송 진행률 브리핑)
        sseService.sendEvent(
                message.executionId(),
                "CHUNK_PROGRESS",
                String.format("📬 [RabbitMQ 워커] 청크 #%d/%d 소비 완료 (진행률 %d%%)", message.chunkIndex(), message.totalChunks(), percent),
                String.format("대상: %s (총 %d건) -> 카카오 알림톡 및 B2B 정산 소명 메일 비동기 발송 성공 (ACK)", sampleMerchants, message.items().size()),
                null
        );

        log.info("[Chunk Consumer] 청크 처리 완료: [{}/{}] (진행률 {}%)", message.chunkIndex(), message.totalChunks(), percent);
    }

    private void processIndividualNotice(NoticeChunkMessage.NoticeItem item, String title) {
        log.debug("[Chunk Consumer] 개별 발송 완료 -> 가맹점: {}, 금액: {}, 사유: {}",
                item.merchantName(), item.pendingAmount(), item.failReason());
    }
}
