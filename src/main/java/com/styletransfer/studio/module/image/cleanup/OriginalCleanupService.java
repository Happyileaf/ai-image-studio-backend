package com.styletransfer.studio.module.image.cleanup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.task.entity.Task;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import com.styletransfer.studio.module.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 原图清理服务（隐私红线）
 *
 * <p>Worker 任务终态时调用 {@link #cleanTask} 删除 task_item 引用的原图临时文件，
 * 并将 sourceFileKey 置空、task.originalCleaned 标记为 1。幂等：已清理则跳过。</p>
 *
 * <p>Task 8 定时清理兜底会复用此类。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OriginalCleanupService {

    private final MinioStorageService minioStorageService;
    private final TaskItemMapper taskItemMapper;
    private final TaskMapper taskMapper;

    /**
     * 清理指定任务的全部原图临时文件（幂等）
     *
     * @param taskId 任务 ID
     */
    public void cleanTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[OriginalCleanup] 任务不存在 taskId={}", taskId);
            return;
        }
        if (task.getOriginalCleaned() != null && task.getOriginalCleaned() == 1) {
            log.debug("[OriginalCleanup] 任务原图已清理，跳过 taskId={}", taskId);
            return;
        }

        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId));

        int cleaned = 0;
        for (TaskItem item : items) {
            if (item.getSourceFileKey() == null || item.getSourceFileKey().isBlank()) {
                continue;
            }
            String bucket = item.getSourceBucket();
            try {
                minioStorageService.deleteObject(bucket, item.getSourceFileKey());
            } catch (Exception e) {
                log.warn("[OriginalCleanup] 删除原图异常 taskId={} itemId={} key={}: {}",
                        taskId, item.getId(), item.getSourceFileKey(), e.getMessage());
            }
            // 无论删除是否成功都置空 key（隐私红线：DB 不再保留原图引用）
            taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, item.getId())
                    .set(TaskItem::getSourceFileKey, null));
            cleaned++;
        }

        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .set(Task::getOriginalCleaned, 1));
        log.info("[OriginalCleanup] 任务原图清理完成 taskId={} cleanedItems={}", taskId, cleaned);
    }
}
