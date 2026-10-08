package com.autoops.mcp.tool.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;

public class NoticeTemplateDto {

    public record Request(
            @JsonPropertyDescription("정산 건 식별자 (예: SETTLE-202609-001)")
            String settlementId,

            @JsonPropertyDescription("가맹점 상호명")
            String merchantName,

            @JsonPropertyDescription("정산 오류/보류 상세 사유")
            String failReason,

            @JsonPropertyDescription("정산 보류 금액")
            BigDecimal pendingAmount
    ) {}

    public record Response(
            String settlementId,
            String merchantName,
            String emailSubject,
            String emailBody,
            String actionGuide
    ) {}
}
