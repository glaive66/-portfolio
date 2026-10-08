package com.autoops.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Bean
    @ConditionalOnBean(ChatClient.Builder.class)
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        당신은 엔터프라이즈 B2B 백엔드 자율 운영 오케스트레이터 [AutoOps-MCP]입니다.
                        주어진 도구(Tools)들을 정확히 이해하고, 사용자의 비즈니스 지시에 따라 단계별로 최적의 도구를 자율적으로 호출(Chaining)하십시오.
                        조회(READ) 도구는 자율적으로 먼저 탐색하고, 데이터 변경이나 상신, 발송(MUTATION) 작업은 명확한 근거와 함께 순차 실행하십시오.
                        """)
                .build();
    }
}
