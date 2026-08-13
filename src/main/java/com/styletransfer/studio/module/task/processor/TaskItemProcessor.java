package com.styletransfer.studio.module.task.processor;

import com.styletransfer.studio.common.enums.TaskItemStatus;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.history.entity.GeneratedImage;
import com.styletransfer.studio.module.history.mapper.GeneratedImageMapper;
import com.styletransfer.studio.module.style.entity.Style;
import com.styletransfer.studio.module.style.mapper.StyleMapper;
import com.styletransfer.studio.module.task.entity.Task;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import com.styletransfer.studio.module.task.mapper.TaskMapper;
import com.styletransfer.studio.module.task.progress.TaskProgressRegistry;
import com.styletransfer.studio.module.task.provider.ImageStyleTransferProvider;
import com.styletransfer.studio.module.task.provider.TransferRequest;
import com.styletransfer.studio.module.task.provider.TransferResult;
import com.styletransfer.studio.module.task.vo.TaskProgressVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务项处理器：单张原图的风格迁移执行单元
 *
 * <p>职责：取消检查 → 调 AI（带重试）→ 拉取结果图上传结果桶 → 写 generated_image → 发 SSE 进度。
 * 异常全部捕获，单个 item 失败不影响其他 item。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskItemProcessor {

    private static final String CANCEL_REQUESTED_MARK = "CANCEL_REQUESTED";
    private static final String CONTENT_DESC = "the original image";
    private static final int SOURCE_URL_EXPIRE_MINUTES = 10;

    private final ImageStyleTransferProvider provider;
    private final MinioStorageService minioStorageService;
    private final TaskItemMapper taskItemMapper;
    private final TaskMapper taskMapper;
    private final GeneratedImageMapper generatedImageMapper;
    private final StyleMapper styleMapper;
    private final TaskProgressRegistry taskProgressRegistry;

    @Value("${app.task.max-retry:2}")
    private int maxRetry;

    @Value("${minio.buckets.result:images-result}")
    private String resultBucket;

    /**
     * 处理单个任务项
     *
     * @param item         任务项
     * @param task         任务（用于 customPrompt / imageCount）
     * @param style        风格
     * @param successCount 成功计数器（跨 item 共享）
     * @param failedCount  失败计数器（跨 item 共享）
     */
    public void process(TaskItem item, Task task, Style style,
                        AtomicInteger successCount, AtomicInteger failedCount) {
        try {
            doProcess(item, task, style, successCount, failedCount);
        } catch (Exception e) {
            log.error("[TaskItemProcessor] 处理异常 taskId={} itemId={}",
                    task.getId(), item.getId(), e);
            markFailed(item, TransferResult.ERR_INTERNAL, "处理异常: " + e.getMessage());
            failedCount.incrementAndGet();
            sendProgress(task, successCount.get(), failedCount.get(), item.getSeq(), TaskItemStatus.FAILED.name());
        }
    }

    private void doProcess(TaskItem item, Task task, Style style,
                           AtomicInteger successCount, AtomicInteger failedCount) {
        Long taskId = task.getId();

        // 1. 取消检查：重新查 task 看 errorMsg 是否含 CANCEL_REQUESTED
        Task latest = taskMapper.selectById(taskId);
        if (latest != null && latest.getErrorMsg() != null
                && latest.getErrorMsg().contains(CANCEL_REQUESTED_MARK)) {
            log.info("[TaskItemProcessor] 任务已请求取消，置 item=CANCELED taskId={} itemId={}",
                    taskId, item.getId());
            taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, item.getId())
                    .set(TaskItem::getStatus, TaskItemStatus.CANCELED.name())
                    .set(TaskItem::getFinishedAt, LocalDateTime.now()));
            failedCount.incrementAndGet();
            sendProgress(task, successCount.get(), failedCount.get(), item.getSeq(), TaskItemStatus.CANCELED.name());
            return;
        }

        // 2. 更新 item=PROCESSING
        taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                .eq(TaskItem::getId, item.getId())
                .set(TaskItem::getStatus, TaskItemStatus.PROCESSING.name())
                .set(TaskItem::getStartedAt, LocalDateTime.now()));

        // 3. 生成原图预签名 URL
        String sourceImageUrl = minioStorageService.presignedGetUrl(
                item.getSourceBucket(), item.getSourceFileKey(), SOURCE_URL_EXPIRE_MINUTES);

        // 4. 构造 prompt
        String prompt = renderPrompt(style.getPromptTemplate(),
                CONTENT_DESC, task.getCustomPrompt());
        TransferRequest request = new TransferRequest(
                sourceImageUrl, prompt, style.getNegativePrompt(), 65);

        // 5. 调 provider（带重试）
        TransferResult result = callWithRetry(request, item);

        if (result.success()) {
            // 6. 成功
            handleSuccess(item, task, style, result, successCount, failedCount);
        } else {
            // 7. 失败
            markFailed(item, result.errorCode(), "AI 迁移失败: " + result.errorCode());
            failedCount.incrementAndGet();
            sendProgress(task, successCount.get(), failedCount.get(), item.getSeq(), TaskItemStatus.FAILED.name());
        }
    }

    /**
     * 调用 provider，重试 ≤ maxRetry 次
     */
    private TransferResult callWithRetry(TransferRequest request, TaskItem item) {
        TransferResult result = null;
        int attempts = 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            attempts = attempt + 1;
            try {
                result = provider.transfer(request);
                if (result.success()) {
                    break;
                }
            } catch (Exception e) {
                String code = (e instanceof java.io.InterruptedIOException
                        || e instanceof InterruptedException
                        || e instanceof java.util.concurrent.TimeoutException)
                        ? TransferResult.ERR_AI_TIMEOUT : TransferResult.ERR_INTERNAL;
                log.warn("[TaskItemProcessor] AI 调用异常 itemId={} attempt={} code={}: {}",
                        item.getId(), attempt, code, e.getMessage());
                result = TransferResult.fail(code);
            }
            if (attempt < maxRetry) {
                log.info("[TaskItemProcessor] 重试 itemId={} attempt={}/{}",
                        item.getId(), attempt + 1, maxRetry);
            }
        }
        // 更新 retryCount（已尝试次数 - 1 表示重试次数）
        int retryUsed = Math.max(0, attempts - 1);
        taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                .eq(TaskItem::getId, item.getId())
                .set(TaskItem::getRetryCount, retryUsed));
        return result;
    }

    /**
     * 成功路径：拉取结果图字节 → 上传结果桶 → 更新 item → 写 generated_image → 发 SSE
     */
    private void handleSuccess(TaskItem item, Task task, Style style, TransferResult result,
                               AtomicInteger successCount, AtomicInteger failedCount) {
        try {
            byte[] bytes = fetchBytes(result.resultImageUrl());
            String objectKey = buildResultObjectKey(task.getUserId());
            minioStorageService.uploadObject(resultBucket, objectKey,
                    new java.io.ByteArrayInputStream(bytes), bytes.length, "image/png");

            LocalDateTime now = LocalDateTime.now();
            taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, item.getId())
                    .set(TaskItem::getStatus, TaskItemStatus.SUCCESS.name())
                    .set(TaskItem::getResultFileKey, objectKey)
                    .set(TaskItem::getResultBucket, resultBucket)
                    .set(TaskItem::getFinishedAt, now));

            GeneratedImage gi = new GeneratedImage();
            gi.setUserId(task.getUserId());
            gi.setTaskId(task.getId());
            gi.setTaskItemId(item.getId());
            gi.setStyleId(style.getId());
            gi.setStyleName(style.getName());
            gi.setFileKey(objectKey);
            gi.setBucket(resultBucket);
            gi.setSize((long) bytes.length);
            generatedImageMapper.insert(gi);

            successCount.incrementAndGet();
            sendProgress(task, successCount.get(), failedCount.get(), item.getSeq(), TaskItemStatus.SUCCESS.name());
            log.info("[TaskItemProcessor] item 处理成功 taskId={} itemId={} key={}",
                    task.getId(), item.getId(), objectKey);
        } catch (Exception e) {
            log.error("[TaskItemProcessor] 成功后处理异常（落盘失败）taskId={} itemId={}",
                    task.getId(), item.getId(), e);
            markFailed(item, TransferResult.ERR_INTERNAL, "结果图落盘失败: " + e.getMessage());
            failedCount.incrementAndGet();
            sendProgress(task, successCount.get(), failedCount.get(), item.getSeq(), TaskItemStatus.FAILED.name());
        }
    }

    private void markFailed(TaskItem item, String errorCode, String errorMsg) {
        taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                .eq(TaskItem::getId, item.getId())
                .set(TaskItem::getStatus, TaskItemStatus.FAILED.name())
                .set(TaskItem::getErrorCode, errorCode)
                .set(TaskItem::getErrorMsg, errorMsg)
                .set(TaskItem::getFinishedAt, LocalDateTime.now()));
    }

    private byte[] fetchBytes(String url) throws Exception {
        try (InputStream in = URI.create(url).toURL().openStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private String buildResultObjectKey(Long userId) {
        return "result/" + userId + "/" + LocalDate.now() + "/" + UUID.randomUUID() + ".png";
    }

    /**
     * 渲染提示词模板：替换 {{content}} / {{user_prompt}}
     */
    private String renderPrompt(String template, String contentDesc, String userPrompt) {
        if (template == null) {
            return "";
        }
        String rendered = template.replace("{{content}}", contentDesc);
        rendered = rendered.replace("{{user_prompt}}", userPrompt == null ? "" : userPrompt);
        return rendered;
    }

    private void sendProgress(Task task, int success, int failed, int currentItem, String stage) {
        int total = task.getImageCount() == null ? 0 : task.getImageCount();
        int done = success + failed;
        int progress = total > 0 ? done * 100 / total : 0;
        TaskProgressVO vo = TaskProgressVO.builder()
                .status("PROCESSING")
                .total(total)
                .success(success)
                .failed(failed)
                .currentItem(currentItem)
                .stage(stage)
                .progress(progress)
                .build();
        taskProgressRegistry.sendProgress(task.getId(), vo);
    }
}
