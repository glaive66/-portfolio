package com.autoops.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ObjectProvider<ChatModel> chatModelProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();

        if (chatModel != null) {
            log.info("[Spring AI] 정규 ChatModel 빈 바인딩 완료: {}", chatModel.getClass().getSimpleName());
            return ChatClient.builder(chatModel)
                    .defaultSystem("""
                            당신은 엔터프라이즈 B2B 백엔드 자율 운영 오케스트레이터 [AutoOps-MCP]입니다.
                            주어진 도구(Tools)들을 정확히 이해하고, 사용자의 비즈니스 지시에 따라 단계별로 최적의 도구를 자율적으로 호출(Chaining)하십시오.
                            조회(READ) 도구는 자율적으로 먼저 탐색하고, 데이터 변경이나 상신, 발송(MUTATION) 작업은 명확한 근거와 함께 순차 실행하십시오.
                            """)
                    .build();
        }

        log.warn("[Spring AI] 외부 LLM ChatModel이 감지되지 않아 Mock Fallback ChatClient로 대체 초기화합니다.");
        return ChatClient.builder(new MockChatModel()).build();
    }

    /**
     * 외부 API 키 미설정 시에도 서버 기동을 보장하는 Mock ChatModel
     */
    static class MockChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            String mockReply = "정산 오류 50건 분석 완료: 계좌 불일치 및 수수료 단수 차액 조치 대상 가맹점 안내문 작성을 완료하였습니다.";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(mockReply))));
        }
    }
}
