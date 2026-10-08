package com.autoops.mcp.saga;

import com.autoops.domain.approval.ApprovalDraft;
import com.autoops.domain.approval.ApprovalDraftRepository;
import com.autoops.domain.approval.ApprovalStatus;
import com.autoops.domain.audit.AiAuditLogRepository;
import com.autoops.infrastructure.rabbitmq.NoticeChunkProducer;
import com.autoops.infrastructure.rabbitmq.dto.NoticeChunkMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private ApprovalDraftRepository approvalDraftRepository;

    @Mock
    private NoticeChunkProducer chunkProducer;

    @Mock
    private AiAuditLogRepository auditLogRepository;

    private SagaOrchestrator sagaOrchestrator;

    @BeforeEach
    void setUp() {
        sagaOrchestrator = new SagaOrchestrator(approvalDraftRepository, chunkProducer, auditLogRepository);
    }

    @Test
    @DisplayName("Saga 정상 흐름: 결재 상신 -> RabbitMQ 청크 3개 분할 발행 완료")
    void testSagaPipelineSuccess() {
        // given
        List<NoticeChunkMessage.NoticeItem> items = List.of(
                new NoticeChunkMessage.NoticeItem("S-01", "한국상사", new BigDecimal("1000"), "계좌오류"),
                new NoticeChunkMessage.NoticeItem("S-02", "넥스트커머스", new BigDecimal("2000"), "수수료오류")
        );

        given(chunkProducer.sendNoticeChunks(anyString(), anyList(), anyString(), anyString(), anyInt()))
                .willReturn(2);

        // when
        SagaOrchestrator.SagaResult result = sagaOrchestrator.executeNoticePipeline(
                "EXEC-100", items, "9월 정산 오류 안내", "안내문 본문", 1
        );

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isCompensated()).isFalse();
        assertThat(result.getPublishedChunks()).isEqualTo(2);
        assertThat(result.getDraftId()).startsWith("DRAFT-");
        verify(approvalDraftRepository).save(any(ApprovalDraft.class));
    }

    @Test
    @DisplayName("Saga 보상 트랜잭션: MQ 청크 발행 장애 시 선행 결재 기안을 REJECTED로 자동 롤백")
    void testSagaCompensatingTransactionOnFailure() {
        // given
        List<NoticeChunkMessage.NoticeItem> items = List.of(
                new NoticeChunkMessage.NoticeItem("S-01", "한국상사", new BigDecimal("1000"), "계좌오류")
        );

        given(chunkProducer.sendNoticeChunks(anyString(), anyList(), anyString(), anyString(), anyInt()))
                .willThrow(new RuntimeException("RabbitMQ Broker Connection Refused (타임아웃 장애)"));

        // when
        SagaOrchestrator.SagaResult result = sagaOrchestrator.executeNoticePipeline(
                "EXEC-200", items, "오류 안내", "본문", 10
        );

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isCompensated()).isTrue();
        assertThat(result.getMessage()).contains("보상 트랜잭션 수행 완료");

        ArgumentCaptor<ApprovalDraft> draftCaptor = ArgumentCaptor.forClass(ApprovalDraft.class);
        verify(approvalDraftRepository, org.mockito.Mockito.atLeast(2)).save(draftCaptor.capture());

        ApprovalDraft finalDraft = draftCaptor.getValue();
        assertThat(finalDraft.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(finalDraft.getApprovalComment()).contains("Saga 장애 복구");
    }
}
