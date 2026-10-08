package com.autoops.infrastructure.rabbitmq;

import com.autoops.mcp.tool.SettlementOpsTools;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue settlementNoticeQueue() {
        return QueueBuilder.durable(SettlementOpsTools.QUEUE_SETTLEMENT_NOTICE)
                .withArgument("x-dead-letter-exchange", "ex.settlement.dlx")
                .withArgument("x-dead-letter-routing-key", "notice.dlq")
                .build();
    }

    @Bean
    public DirectExchange settlementExchange() {
        return new DirectExchange(SettlementOpsTools.EXCHANGE_SETTLEMENT, true, false);
    }

    @Bean
    public Binding settlementNoticeBinding(Queue settlementNoticeQueue, DirectExchange settlementExchange) {
        return BindingBuilder.bind(settlementNoticeQueue)
                .to(settlementExchange)
                .with(SettlementOpsTools.ROUTING_KEY_NOTICE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
