package com.autoops.infrastructure.rabbitmq;

import com.autoops.infrastructure.rabbitmq.dto.NoticeChunkMessage;
import com.autoops.mcp.tool.SettlementOpsTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoticeChunkConsumer {

    /**
     * Chunk 분할 알림 메시지 비동기 수신 및 병렬 처리
     */
    @RabbitListener(queues = SettlementOpsTools.QUEUE_SETTLEMENT_NOTICE)
    public void consumeNoticeChunk(NoticeChunkMessage message) {
        log.info("[Chunk Consumer] 청크 수신 - ExecutionId: {}, 청크 번호: [{}/{}], 포함 건수: {}건",
                message.executionId(), message.chunkIndex(), message.totalChunks(), message.items().size());

        for (NoticeChunkMessage.NoticeItem item : message.items()) {
            try {
                // 실무 환경: 외부 카카오 알림톡/SMS/이메일 발송 API 연동 시뮬레이션
                processIndividualNotice(item, message.templateTitle());
            } catch (Exception e) {
                log.error("[Chunk Consumer] 개별 알림 발송 중 오류 발생 - SettlementId: {}, 사유: {}",
                        item.settlementId(), e.getMessage());
            }
        }

        log.info("[Chunk Consumer] 청크 처리 완료: [{}/{}]", message.chunkIndex(), message.totalChunks());
    }

    private void processIndividualNotice(NoticeChunkMessage.NoticeItem item, String title) {
        log.debug("[Chunk Consumer] 개별 발송 완료 -> 가맹점: {}, 금액: {}, 사유: {}",
                item.merchantName(), item.pendingAmount(), item.failReason());
    }
}
