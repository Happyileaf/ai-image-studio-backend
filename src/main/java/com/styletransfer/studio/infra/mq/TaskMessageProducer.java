package com.styletransfer.studio.infra.mq;

import com.styletransfer.studio.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 任务创建消息生产者
 *
 * <p>投递到 {@link RabbitMQConfig#EXCHANGE_STYLE_TRANSFER}，
 * routingKey = {@link RabbitMQConfig#ROUTING_KEY_TASK_CREATE}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 投递任务创建消息。
     *
     * @param taskId 任务 ID
     */
    public void sendCreate(Long taskId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_STYLE_TRANSFER,
                RabbitMQConfig.ROUTING_KEY_TASK_CREATE,
                new TaskMessage(taskId));
        log.info("[TaskMessageProducer] 投递任务创建消息 taskId={}", taskId);
    }
}
