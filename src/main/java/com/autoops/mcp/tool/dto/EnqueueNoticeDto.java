package com.autoops.mcp.tool.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public class EnqueueNoticeDto {

    public record Request(
            @JsonPropertyDescription("안내문을 발송할 정산 건 식별자 목록")
            List<String> settlementIds,

            @JsonPropertyDescription("발송할 안내문 템플릿 제목")
            String templateTitle,

            @JsonPropertyDescription("발송할 안내문 공통 본문")
            String messageTemplate
    ) {}

    public record Response(
            int requestedCount,
            int queuedCount,
            String queueName,
            String status,
            String message
    ) {}
}
