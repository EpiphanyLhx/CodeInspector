package com.codeinspector.mq.consumer;

import com.codeinspector.config.RabbitMQConfig;
import com.codeinspector.model.entity.ReviewTask;
import com.codeinspector.service.ReviewService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 审查任务消费者 - 异步消费RabbitMQ队列中的审查任务
 * 解决大模型响应慢的问题，避免前端请求超时
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class ReviewTaskConsumer {

    private final ReviewService reviewService;

    /**
     * 监听审查队列，消费审查任务
     * 手动ACK确保任务可靠性
     */
    @RabbitListener(queues = RabbitMQConfig.REVIEW_QUEUE, concurrency = "2")
    public void handleReviewTask(ReviewTask task, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到审查任务[{}], 项目[{}], 切片[{}]", task.getId(), task.getProjectId(), task.getChunkId());
        try {
            reviewService.processChunkReview(task);
            // 手动ACK确认消费成功
            channel.basicAck(deliveryTag, false);
            log.info("审查任务[{}]处理完成", task.getId());
        } catch (Exception e) {
            log.error("审查任务[{}]处理失败: ", task.getId(), e);
            try {
                // 拒绝并重新入队（最多重试3次）
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("消息重试失败: ", ex);
            }
        }
    }

    /**
     * 处理死信队列 - 记录失败的任务
     */
    @RabbitListener(queues = RabbitMQConfig.REVIEW_DLX_QUEUE)
    public void handleDeadLetter(ReviewTask task, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.warn("审查任务[{}]进入死信队列", task.getId());
        try {
            // 标记任务失败
            task.setStatus("FAILED");
            task.setErrorMsg("审查超时，任务自动失败");
            reviewService.processChunkReview(task);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("死信处理异常: ", e);
        }
    }
}
