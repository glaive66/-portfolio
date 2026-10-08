package com.autoops.mcp.tool.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;

public class SettlementQueryDto {

    public record Request(
            @JsonPropertyDescription("정산 연월 (형식: YYYYMM, 예: 202609)")
            String yearMonth,

            @JsonPropertyDescription("최소 조회 오류 건수 제한 (기본값: 10)")
            Integer limit
    ) {}

    public record Response(
            String yearMonth,
            int totalErrorCount,
            BigDecimal totalPendingAmount,
            List<Item> errorList
    ) {}

    public record Item(
            String settlementId,
            String merchantName,
            String settlementYm,
            String status,
            String failReason,
            BigDecimal pendingAmount
    ) {}
}
