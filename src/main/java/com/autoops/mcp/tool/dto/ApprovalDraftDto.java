package com.autoops.mcp.tool.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;

public class ApprovalDraftDto {

    public record Request(
            @JsonPropertyDescription("전자결재 기안 제목")
            String title,

            @JsonPropertyDescription("전자결재 기안 상세 본문 내용")
            String content,

            @JsonPropertyDescription("대상 가맹점 수")
            Integer targetMerchantCount,

            @JsonPropertyDescription("총 정산 보류 금액")
            BigDecimal totalPendingAmount
    ) {}

    public record Response(
            String draftId,
            String title,
            String status,
            String message
    ) {}
}
