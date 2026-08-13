package com.styletransfer.studio.module.task.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import com.styletransfer.studio.common.enums.TaskItemStatus;
import com.styletransfer.studio.common.enums.TaskStatus;
import com.styletransfer.studio.config.RabbitMQConfig;
import com.styletransfer.studio.config.ThreadPoolConfig;
import com.styletransfer.studio.infra.mq.TaskMessage;
import com.styletransfer.studio.module.image.cleanup.OriginalCleanupService;
import com.styletransfer.studio.module.quota.service.QuotaService;
import com.styletransfer.studio.module.style.entity.Style;
import com.styletransfer.studio.module.style.mapper.StyleMapper;
import com.styletransfer.studio.module.task.entity.Task;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import com.styletransfer.studio.module.task.mapper.TaskMapper;
import com.styletransfer.studio.module.task.processor.TaskItemProcessor;
import com.styletransfer.studio.module.task.progress.TaskProgressRegistry;
import com.styletransfer.studio.module.task.provider.TransferResult;
import com.styletransfer.studio.module.task.vo.TaskProgressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务消费者：异步执行风格迁移任务
 *
 * <p>手动 ACK：处理成功 basicAck；异常时把任务置 FAILED + 清理原图 + 回补额度后 ACK（防死循环卡死）。</p>
 * <p>多图并发：用 {@link ThreadPoolConfig#TASK_PROCESSOR_POOL}（core=9）并发处理单个任务的 items。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumer {

    private static final String CANCEL_REQUESTED_MARK = "CANCEL_REQUESTED";

    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final TaskItemProcessor taskItemProcessor;
    private final OriginalCleanupService originalCleanupService;
    private final QuotaService quotaService;
    private final TaskProgressRegistry taskProgressRegistry;
    private final StyleMapper styleMapper;
    @Qualifier(ThreadPoolConfig.TASK_PROCESSOR_POOL)
    private final ThreadPoolExecutor taskProcessorPool;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_TASK_CREATE)
    public void consume(TaskMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        Long taskId = message.taskId();
        log.info("[TaskConsumer] 收到任务消息 taskId={}", taskId);
        try {
            doConsume(taskId);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[TaskConsumer] 消费异常 taskId={}, 置 FAILED + 回补额度后 ACK", taskId, e);
            handleConsumeFailure(taskId);
            safeAck(channel, tag);
        }
    }

    private void doConsume(Long taskId) {
        // 1. 查 task；不存在或非 PENDING → 直接 ACK（防重复消费/脏数据）
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[TaskConsumer] 任务不存在 taskId={}, 跳过", taskId);
            return;
        }
        if (!TaskStatus.PENDING.name().equals(task.getStatus())) {
            log.warn("[TaskConsumer] 任务状态非 PENDING taskId={} status={}, 跳过",
                    taskId, task.getStatus());
            return;
        }

        // 2. 更新 task=PROCESSING
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.PROCESSING.name())
                .set(Task::getStartedAt, LocalDateTime.now()));
        task.setStatus(TaskStatus.PROCESSING.name());

        // 3. 查 items（seq asc）
        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .orderByAsc(TaskItem::getSeq));

        // 4. 查 style
        Style style = styleMapper.selectById(task.getStyleId());
        if (style == null) {
            log.error("[TaskConsumer] 风格不存在 taskId={} styleId={}, 置 FAILED", taskId, task.getStyleId());
            finishTask(taskId, TaskStatus.FAILED, 0, items.size(), "风格不存在");
            originalCleanupService.cleanTask(taskId);
            sendDone(taskId, TaskStatus.FAILED.name(), 0, items.size(), task.getImageCount());
            return;
        }

        // 5. 并发处理 items
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        List<? extends Future<?>> futures = items.stream()
                .map(it -> taskProcessorPool.submit(() ->
                        taskItemProcessor.process(it, task, style, successCount, failedCount)))
                .toList();

        // 6. 等待全部完成
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                log.error("[TaskConsumer] item future 异常 taskId={}", taskId, e);
            }
        }

        // 7. 重新查 items 汇总
        List<TaskItem> finalItems = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId));
        int success = 0;
        int failed = 0;
        int canceled = 0;
        for (TaskItem it : finalItems) {
            String s = it.getStatus();
            if (TaskItemStatus.SUCCESS.name().equals(s)) {
                success++;
            } else if (TaskItemStatus.CANCELED.name().equals(s)) {
                canceled++;
            } else {
                // FAILED / 异常遗留的 PROCESSING / PENDING 都计为失败
                failed++;
            }
        }
        int totalFail = failed + canceled;

        // 8. 判定终态
        Task latest = taskMapper.selectById(taskId);
        boolean cancelled = latest != null && latest.getErrorMsg() != null
                && latest.getErrorMsg().contains(CANCEL_REQUESTED_MARK);
        TaskStatus terminal;
        String errorMsg = null;
        if (cancelled) {
            terminal = TaskStatus.CANCELED;
        } else if (success == task.getImageCount()) {
            terminal = TaskStatus.SUCCESS;
        } else {
            terminal = TaskStatus.FAILED;
            errorMsg = "部分图片处理失败 success=" + success + " failed=" + totalFail;
        }

        // 9. 更新 task 终态
        finishTask(taskId, terminal, success, totalFail, errorMsg);

        // 10. 原图清理
        originalCleanupService.cleanTask(taskId);

        // 11. 额度回补：仅系统失败（AI_TIMEOUT / INTERNAL）回补，CONTENT_VIOLATION / CANCELED 不回补
        if (!cancelled) {
            int refundCount = 0;
            for (TaskItem it : finalItems) {
                if (TaskItemStatus.FAILED.name().equals(it.getStatus())) {
                    String code = it.getErrorCode();
                    if (TransferResult.ERR_AI_TIMEOUT.equals(code)
                            || TransferResult.ERR_INTERNAL.equals(code)) {
                        refundCount++;
                    }
                }
            }
            if (refundCount > 0) {
                try {
                    quotaService.refund(task.getUserId(), refundCount, taskId, null,
                            QuotaService.REASON_TASK_FAIL_REFUND);
                    log.info("[TaskConsumer] 额度回补 taskId={} userId={} count={}",
                            taskId, task.getUserId(), refundCount);
                } catch (Exception e) {
                    log.error("[TaskConsumer] 额度回补失败 taskId={}", taskId, e);
                }
            }
        }

        // 12. 发 SSE done
        sendDone(taskId, terminal.name(), success, totalFail, task.getImageCount());
        log.info("[TaskConsumer] 任务处理完成 taskId={} terminal={} success={} failed={}",
                taskId, terminal, success, totalFail);
    }

    /**
     * 消费异常兜底：task 置 FAILED + 清理原图 + 回补全部额度
     */
    private void handleConsumeFailure(Long taskId) {
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            int imageCount = task.getImageCount() == null ? 0 : task.getImageCount();
            finishTask(taskId, TaskStatus.FAILED, 0, imageCount, "INTERNAL_ERROR");
            originalCleanupService.cleanTask(taskId);
            if (imageCount > 0) {
                try {
                    quotaService.refund(task.getUserId(), imageCount, taskId, null,
                            QuotaService.REASON_TASK_FAIL_REFUND);
                } catch (Exception e) {
                    log.error("[TaskConsumer] 异常兜底额度回补失败 taskId={}", taskId, e);
                }
            }
            sendDone(taskId, TaskStatus.FAILED.name(), 0, imageCount, imageCount);
        } catch (Exception e) {
            log.error("[TaskConsumer] 异常兜底处理失败 taskId={}", taskId, e);
        }
    }

    private void finishTask(Long taskId, TaskStatus status, int success, int fail, String errorMsg) {
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, status.name())
                .set(Task::getSuccessCount, success)
                .set(Task::getFailCount, fail)
                .set(Task::getFinishedAt, LocalDateTime.now())
                .set(errorMsg != null, Task::getErrorMsg, errorMsg));
    }

    private void sendDone(Long taskId, String status, int success, int failed, int total) {
        TaskProgressVO vo = TaskProgressVO.builder()
                .status(status)
                .total(total)
                .success(success)
                .failed(failed)
                .currentItem(0)
                .stage(status)
                .progress(100)
                .build();
        taskProgressRegistry.sendDone(taskId, vo);
    }

    private void safeAck(Channel channel, long tag) {
        try {
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[TaskConsumer] safeAck 失败 tag={}", tag, e);
        }
    }
}
