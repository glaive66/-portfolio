package com.autoops.domain.settlement;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class SettlementSummaryDto {
    private String settlementYm;
    private long totalCount;
    private long errorCount;
    private long pendingCount;
    private BigDecimal totalPendingAmount;
}
