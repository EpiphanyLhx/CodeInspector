package com.codeinspector.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class RabbitMQConfig {

    // 交换机
    public static final String REVIEW_EXCHANGE = "code.review.exchange";
    // 队列
    public static final String REVIEW_QUEUE = "code.review.queue";
    // 死信队列
    public static final String REVIEW_DLX_QUEUE = "code.review.dlx.queue";
    public static final String REVIEW_DLX_EXCHANGE = "code.review.dlx.exchange";
    // 路由键
    public static final String REVIEW_ROUTING_KEY = "code.review.routing";
    public static final String REVIEW_DLX_ROUTING_KEY = "code.review.dlx.routing";

    @Bean
    public DirectExchange reviewExchange() {
        return ExchangeBuilder.directExchange(REVIEW_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange reviewDlxExchange() {
        return ExchangeBuilder.directExchange(REVIEW_DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue reviewQueue() {
        return QueueBuilder.durable(REVIEW_QUEUE)
                .deadLetterExchange(REVIEW_DLX_EXCHANGE)
                .deadLetterRoutingKey(REVIEW_DLX_ROUTING_KEY)
                .ttl(600000) // 10分钟超时
                .maxLength(1000)
                .build();
    }

    @Bean
    public Queue reviewDlxQueue() {
        return QueueBuilder.durable(REVIEW_DLX_QUEUE).build();
    }

    @Bean
    public Binding reviewBinding() {
        return BindingBuilder.bind(reviewQueue()).to(reviewExchange()).with(REVIEW_ROUTING_KEY);
    }

    @Bean
    public Binding reviewDlxBinding() {
        return BindingBuilder.bind(reviewDlxQueue()).to(reviewDlxExchange()).with(REVIEW_DLX_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
