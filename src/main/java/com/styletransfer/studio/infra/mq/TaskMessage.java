package com.styletransfer.studio.infra.mq;

/**
 * 任务创建 MQ 消息体
 *
 * <p>使用 {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}
 * 以 JSON 文本格式传输（在 {@code RabbitMQConfig.messageConverter()} 中统一配置），
 * 因此无需实现 Serializable，Jackson 可直接序列化 record。</p>
 *
 * <p>Worker 消费时反序列化拿到 taskId 后拉取任务详情。</p>
 *
 * @param taskId 任务 ID
 */
public record TaskMessage(Long taskId) {
}
