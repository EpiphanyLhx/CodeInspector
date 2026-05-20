package com.codeinspector.mq.producer;

import com.codeinspector.config.RabbitMQConfig;
import com.codeinspector.model.entity.ReviewTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 审查任务生产者 - 将任务发送到RabbitMQ队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class ReviewTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送审查任务到消息队列
     * 实现异步调度，解决大模型响应慢导致的前端超时问题
     */
    public void sendReviewTask(ReviewTask task) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.REVIEW_EXCHANGE,
                    RabbitMQConfig.REVIEW_ROUTING_KEY,
                    task
            );
            log.info("审查任务[{}]已发送到队列", task.getId());
        } catch (Exception e) {
            log.error("发送审查任务[{}]失败: {}", task.getId(), e.getMessage());
            throw new RuntimeException("审查任务入队失败", e);
        }
    }
}
