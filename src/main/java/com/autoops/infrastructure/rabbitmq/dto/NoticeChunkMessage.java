package com.autoops.infrastructure.rabbitmq.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

public record NoticeChunkMessage(
        String executionId,
        int chunkIndex,
        int totalChunks,
        int totalItemCount,
        List<NoticeItem> items,
        String templateTitle,
        String templateBody
) implements Serializable {

    public record NoticeItem(
            String settlementId,
            String merchantName,
            BigDecimal pendingAmount,
            String failReason
    ) implements Serializable {}
}
