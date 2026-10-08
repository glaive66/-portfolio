package com.autoops.domain.settlement;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TB_MERCHANT_SETTLEMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantSettlement {

    @Id
    @Column(name = "SETTLEMENT_ID", length = 32)
    private String settlementId;

    @Column(name = "MERCHANT_NAME", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "SETTLEMENT_YM", nullable = false, length = 6)
    private String settlementYm;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "FAIL_REASON", length = 255)
    private String failReason;

    @Column(name = "PENDING_AMOUNT", nullable = false, precision = 15, scale = 2)
    private BigDecimal pendingAmount;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Builder
    public MerchantSettlement(String settlementId, String merchantName, String settlementYm,
                              SettlementStatus status, String failReason, BigDecimal pendingAmount) {
        this.settlementId = settlementId;
        this.merchantName = merchantName;
        this.settlementYm = settlementYm;
        this.status = status;
        this.failReason = failReason;
        this.pendingAmount = pendingAmount;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(SettlementStatus status, String failReason) {
        this.status = status;
        this.failReason = failReason;
        this.updatedAt = LocalDateTime.now();
    }
}
