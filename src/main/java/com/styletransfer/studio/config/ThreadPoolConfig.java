package com.styletransfer.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 多图并发处理线程池配置
 *
 * <p>一个任务 1~9 张图，内部用该线程池并发调用 AI 模型。
 * 单用户并发限制（同时仅 1 个进行中任务）在 Service 层通过 Redisson 分布式锁保证。</p>
 */
@Configuration
public class ThreadPoolConfig {

    /** 线程池 Bean 名称（@Qualifier 用） */
    public static final String TASK_PROCESSOR_POOL = "taskProcessorPool";

    @Bean(TASK_PROCESSOR_POOL)
    public ThreadPoolExecutor taskProcessorPool() {
        return new ThreadPoolExecutor(
                9,                                // corePoolSize：匹配单任务最大 9 张图
                9,                                // maximumPoolSize：不超配，避免跨任务过度抢占
                60L, TimeUnit.SECONDS,            // keepAlive：空闲线程回收
                new ArrayBlockingQueue<>(100),    // queueCapacity：排队任务上限
                // 自定义命名线程（便于日志排查）
                r -> {
                    Thread t = new Thread(r);
                    t.setName("task-processor-" + t.getId());
                    return t;
                },
                // CallerRunsPolicy：队列满时让调用线程自行执行（降载降级，不丢任务）
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
