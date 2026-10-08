package com.autoops.mcp.tool;

import com.autoops.domain.approval.ApprovalDraft;
import com.autoops.domain.approval.ApprovalDraftRepository;
import com.autoops.domain.settlement.MerchantSettlement;
import com.autoops.domain.settlement.MerchantSettlementRepository;
import com.autoops.domain.settlement.SettlementStatus;
import com.autoops.mcp.tool.dto.ApprovalDraftDto;
import com.autoops.mcp.tool.dto.EnqueueNoticeDto;
import com.autoops.mcp.tool.dto.NoticeTemplateDto;
import com.autoops.mcp.tool.dto.SettlementQueryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementOpsToolsTest {

    @Mock
    private MerchantSettlementRepository settlementRepository;

    @Mock
    private ApprovalDraftRepository approvalDraftRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private SettlementOpsTools settlementOpsTools;

    @BeforeEach
    void setUp() {
        settlementOpsTools = new SettlementOpsTools(settlementRepository, approvalDraftRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("Tool 1: 정산 오류 가맹점 목록 조회 및 집계 검증")
    void testFindSettlementErrors() {
        // given
        MerchantSettlement settlement1 = MerchantSettlement.builder()
                .settlementId("SETTLE-202609-001")
                .merchantName("(주)한국상사")
                .settlementYm("202609")
                .status(SettlementStatus.ERROR)
                .failReason("계좌 불일치")
                .pendingAmount(new BigDecimal("12000000"))
                .build();

        MerchantSettlement settlement2 = MerchantSettlement.builder()
                .settlementId("SETTLE-202609-002")
                .merchantName("넥스트커머스")
                .settlementYm("202609")
                .status(SettlementStatus.ERROR)
                .failReason("수수료 차액")
                .pendingAmount(new BigDecimal("8000000"))
                .build();

        given(settlementRepository.findBySettlementYmAndStatus("202609", SettlementStatus.ERROR))
                .willReturn(List.of(settlement1, settlement2));

        SettlementQueryDto.Request request = new SettlementQueryDto.Request("202609", 10);

        // when
        SettlementQueryDto.Response response = settlementOpsTools.findSettlementErrors(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.yearMonth()).isEqualTo("202609");
        assertThat(response.totalErrorCount()).isEqualTo(2);
        assertThat(response.totalPendingAmount()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(response.errorList()).hasSize(2);
    }

    @Test
    @DisplayName("Tool 2: 맞춤형 정산 안내문 템플릿 생성 검증")
    void testGenerateNoticeTemplate() {
        // given
        NoticeTemplateDto.Request request = new NoticeTemplateDto.Request(
                "SETTLE-202609-001",
                "(주)한국상사",
                "예금주명 불일치",
                new BigDecimal("12500000")
        );

        // when
        NoticeTemplateDto.Response response = settlementOpsTools.generateNoticeTemplate(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.emailSubject()).contains("(주)한국상사", "정산 보류 안내");
        assertThat(response.emailBody()).contains("12,500,000 원", "예금주명 불일치");
    }

    @Test
    @DisplayName("Tool 3: 전자결재 상신 기안 생성 검증")
    void testCreateApprovalDraft() {
        // given
        ApprovalDraftDto.Request request = new ApprovalDraftDto.Request(
                "[정산 재처리] 2026년 9월 가맹점 50곳 오류 소명 조치 상신 건",
                "상세 결재 내용입니다.",
                50,
                new BigDecimal("500000000")
        );

        // when
        ApprovalDraftDto.Response response = settlementOpsTools.createApprovalDraft(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.draftId()).startsWith("DRAFT-");
        assertThat(response.status()).isEqualTo("DRAFTED");
        verify(approvalDraftRepository).save(any(ApprovalDraft.class));
    }

    @Test
    @DisplayName("Tool 4: RabbitMQ 비동기 대량 알림 큐 적재 검증")
    void testEnqueueBulkNotice() {
        // given
        EnqueueNoticeDto.Request request = new EnqueueNoticeDto.Request(
                List.of("SETTLE-202609-001", "SETTLE-202609-002", "SETTLE-202609-003"),
                "[정산 안내] 보류 건 안내",
                "안내 내용"
        );

        // when
        EnqueueNoticeDto.Response response = settlementOpsTools.enqueueBulkNotice(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.requestedCount()).isEqualTo(3);
        assertThat(response.queuedCount()).isEqualTo(3);
        assertThat(response.queueName()).isEqualTo(SettlementOpsTools.QUEUE_SETTLEMENT_NOTICE);
    }
}
