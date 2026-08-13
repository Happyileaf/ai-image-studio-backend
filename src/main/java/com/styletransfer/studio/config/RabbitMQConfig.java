package com.styletransfer.studio.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 交换机 / 队列 / 绑定 声明（骨架）
 *
 * <p>MVP 阶段使用经典 Direct Exchange 路由模型：
 * <pre>
 * Exchange: style.transfer.exchange
 *   Queue:    task.create.queue      (routing-key: task.create)       → Worker 消费任务
 *   DLQ:      task.create.dlq        (routing-key: task.create.dlq)   → 死信队列（失败消息积压/人工排查）
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    /** 主交换机 */
    public static final String EXCHANGE_STYLE_TRANSFER = "style.transfer.exchange";

    /** 任务创建队列 */
    public static final String QUEUE_TASK_CREATE = "task.create.queue";
    public static final String ROUTING_KEY_TASK_CREATE = "task.create";

    /** 死信队列（消息过期/拒绝后转到这里） */
    public static final String QUEUE_TASK_CREATE_DLQ = "task.create.dlq";
    public static final String ROUTING_KEY_TASK_CREATE_DLQ = "task.create.dlq";

    @Bean
    public DirectExchange styleTransferExchange() {
        return new DirectExchange(EXCHANGE_STYLE_TRANSFER, true, false);
    }

    /** 任务创建主队列：带 DLX（拒绝/过期消息转发到死信交换机=同一交换机，DLQ routing-key） */
    @Bean
    public Queue taskCreateQueue() {
        return QueueBuilder.durable(QUEUE_TASK_CREATE)
                .deadLetterExchange(EXCHANGE_STYLE_TRANSFER)
                .deadLetterRoutingKey(ROUTING_KEY_TASK_CREATE_DLQ)
                .build();
    }

    @Bean
    public Queue taskCreateDlq() {
        return QueueBuilder.durable(QUEUE_TASK_CREATE_DLQ).build();
    }

    @Bean
    public Binding taskCreateBinding() {
        return BindingBuilder.bind(taskCreateQueue())
                .to(styleTransferExchange())
                .with(ROUTING_KEY_TASK_CREATE);
    }

    @Bean
    public Binding taskCreateDlqBinding() {
        return BindingBuilder.bind(taskCreateDlq())
                .to(styleTransferExchange())
                .with(ROUTING_KEY_TASK_CREATE_DLQ);
    }
}
