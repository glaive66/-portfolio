package com.autoops.infrastructure.rabbitmq;

import com.autoops.infrastructure.rabbitmq.dto.NoticeChunkMessage;
import com.autoops.mcp.tool.SettlementOpsTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeChunkProducer {

    private final RabbitTemplate rabbitTemplate;

    public static final int DEFAULT_CHUNK_SIZE = 10;

    /**
     * 대량 정산 알림 대상을 Chunk 단위로 분할하여 RabbitMQ에 비동기 발행
     *
     * @param executionId 실행 고유 ID
     * @param items       전체 안내 대상 아이템 목록
     * @param title       공통 안내 제목
     * @param body        공통 안내 본문
     * @param chunkSize   청크 크기 (기본 10)
     * @return 발행된 총 청크 수
     */
    public int sendNoticeChunks(String executionId, List<NoticeChunkMessage.NoticeItem> items,
                                String title, String body, int chunkSize) {
        if (items == null || items.isEmpty()) {
            log.warn("[Chunk Producer] 발행할 대상 아이템이 비어 있습니다. (ExecutionId: {})", executionId);
            return 0;
        }

        int targetChunkSize = (chunkSize > 0) ? chunkSize : DEFAULT_CHUNK_SIZE;
        int totalItems = items.size();
        int totalChunks = (int) Math.ceil((double) totalItems / targetChunkSize);

        log.info("[Chunk Producer] 대량 알림 분할 발행 시작 - 총 {}건, 청크 크기: {}, 총 청크 수: {} (ExecutionId: {})",
                totalItems, targetChunkSize, totalChunks, executionId);

        for (int i = 0; i < totalChunks; i++) {
            int fromIndex = i * targetChunkSize;
            int toIndex = Math.min(fromIndex + targetChunkSize, totalItems);
            List<NoticeChunkMessage.NoticeItem> chunkItems = new ArrayList<>(items.subList(fromIndex, toIndex));

            NoticeChunkMessage chunkMessage = new NoticeChunkMessage(
                    executionId,
                    i + 1,
                    totalChunks,
                    totalItems,
                    chunkItems,
                    title,
                    body
            );

            rabbitTemplate.convertAndSend(
                    SettlementOpsTools.EXCHANGE_SETTLEMENT,
                    SettlementOpsTools.ROUTING_KEY_NOTICE,
                    chunkMessage
            );

            log.info("[Chunk Producer] 청크 발행 완료: [{}/{}] (항목수: {}건)", (i + 1), totalChunks, chunkItems.size());
        }

        return totalChunks;
    }
}
