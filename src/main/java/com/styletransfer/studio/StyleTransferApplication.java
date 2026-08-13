package com.styletransfer.studio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 图片风格迁移工作台 - 后端启动类
 *
 * <p>技术基线：Spring Boot 4.1 + Java 25 LTS</p>
 * <ul>
 *   <li>Web API + RabbitMQ Worker 同进程运行</li>
 *   <li>Worker 内部用线程池并发处理一个任务中的多张图</li>
 *   <li>启用定时任务：原图兜底清理 / 结果图到期清理 / 孤儿图片扫描</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class StyleTransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(StyleTransferApplication.class, args);
    }
}
