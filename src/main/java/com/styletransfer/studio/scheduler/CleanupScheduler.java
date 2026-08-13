package com.styletransfer.studio.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.history.entity.GeneratedImage;
import com.styletransfer.studio.module.history.mapper.GeneratedImageMapper;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M7 清理与对账模块：定时兜底清理
 *
 * <ul>
 *   <li>{@link #cleanExpiredOriginals()} 原图兜底清理，每 30min：清理 task_item 中
 *       source_file_key 不为空且创建时间超过 {@code app.cleanup.original-retention}（默认 2h）的原图临时文件。</li>
 *   <li>{@link #cleanOrphanTempImages()} 孤儿原图清理，每日 03:00：扫描 source-temp bucket，
 *       删除 lastModified 超过 {@code app.cleanup.orphan-retention}（默认 24h）的对象（上传但未创建任务的孤立原图）。</li>
 *   <li>{@link #cleanExpiredResultImages()} 结果图到期清理，每日 03:30：清理 generated_image 中
 *       创建时间超过 {@code app.history.result-image-retention-days}（默认 7 天）的结果图文件与记录（软删 deleted=1）。</li>
 * </ul>
 *
 * <p>复用 {@link MinioStorageService#deleteObject} 的幂等删除；孤儿扫描需列举对象，直接注入 {@link MinioClient}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupScheduler {

    private final MinioStorageService minioStorageService;
    private final MinioClient minioClient;
    private final TaskItemMapper taskItemMapper;
    private final GeneratedImageMapper generatedImageMapper;

    @Value("${minio.buckets.source-temp}")
    private String sourceTempBucket;

    @Value("${minio.buckets.result}")
    private String resultBucket;

    /** 原图兜底清理阈值（默认 2h） */
    @Value("${app.cleanup.original-retention:2h}")
    private Duration originalRetention;

    /** 孤儿原图清理阈值（默认 24h） */
    @Value("${app.cleanup.orphan-retention:24h}")
    private Duration orphanRetention;

    /** 结果图保留天数（默认 7 天） */
    @Value("${app.history.result-image-retention-days:7}")
    private int resultRetentionDays;

    /**
     * 原图兜底清理：每 30min
     *
     * <p>隐私红线：终态清理（Task6 OriginalCleanupService）已正常执行时本任务通常空跑；
     * 此处兜底覆盖异常路径（Worker 崩溃 / 未触发终态清理）残留的原图临时文件。</p>
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void cleanExpiredOriginals() {
        LocalDateTime threshold = LocalDateTime.now().minus(originalRetention);
        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .isNotNull(TaskItem::getSourceFileKey)
                .lt(TaskItem::getCreatedAt, threshold));
        if (items.isEmpty()) {
            log.debug("[CleanupScheduler] 原图兜底清理：无可清理项");
            return;
        }
        int cleaned = 0;
        for (TaskItem item : items) {
            String key = item.getSourceFileKey();
            String bucket = (item.getSourceBucket() != null && !item.getSourceBucket().isBlank())
                    ? item.getSourceBucket() : sourceTempBucket;
            try {
                minioStorageService.deleteObject(bucket, key);
            } catch (Exception e) {
                log.warn("[CleanupScheduler] 删除原图异常 itemId={} bucket={} key={}: {}",
                        item.getId(), bucket, key, e.getMessage());
            }
            // 无论删除是否成功都置空 key（隐私红线：DB 不再保留原图引用）
            taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, item.getId())
                    .set(TaskItem::getSourceFileKey, null));
            cleaned++;
        }
        log.info("[CleanupScheduler] 原图兜底清理完成 cleanedItems={}/{}", cleaned, items.size());
    }

    /**
     * 孤儿原图清理：每日 03:00
     *
     * <p>扫描 source-temp bucket，删除 lastModified 超过 orphan-retention（默认 24h）的对象。
     * 这些是用户上传后未创建任务（或终态清理后残留）的孤立原图，按时间维度直接清理。</p>
     */
    @Scheduled(cron = "${app.cleanup.orphan-cron:0 0 3 * * *}")
    public void cleanOrphanTempImages() {
        Instant threshold = Instant.now().minus(orphanRetention);
        int scanned = 0;
        int deleted = 0;
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(sourceTempBucket)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item;
                try {
                    item = result.get();
                } catch (Exception e) {
                    log.warn("[CleanupScheduler] 遍历 source-temp 对象异常: {}", e.getMessage());
                    continue;
                }
                scanned++;
                if (item.lastModified() == null) {
                    continue;
                }
                // 用 Instant 比较规避 MinIO 返回 UTC 与本地时区差异
                if (item.lastModified().toInstant().isBefore(threshold)) {
                    minioStorageService.deleteObject(sourceTempBucket, item.objectName());
                    deleted++;
                }
            }
        } catch (Exception e) {
            log.error("[CleanupScheduler] 孤儿原图扫描失败 bucket={}", sourceTempBucket, e);
        }
        log.info("[CleanupScheduler] 孤儿原图清理完成 scanned={} deleted={}", scanned, deleted);
    }

    /**
     * 结果图到期清理：每日 03:30
     *
     * <p>清理 generated_image 中创建时间超过 result-image-retention-days（默认 7 天）的结果图：
     * 删除 MinIO 对象 + 软删数据库记录（deleted=1，MyBatis-Plus @TableLogic 由 deleteById 完成）。</p>
     */
    @Scheduled(cron = "${app.cleanup.result-cron:0 30 3 * * *}")
    public void cleanExpiredResultImages() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(resultRetentionDays);
        // GeneratedImage 带 @TableLogic，selectList 自动追加 deleted=0
        List<GeneratedImage> images = generatedImageMapper.selectList(new LambdaQueryWrapper<GeneratedImage>()
                .lt(GeneratedImage::getCreatedAt, threshold));
        if (images.isEmpty()) {
            log.debug("[CleanupScheduler] 结果图到期清理：无可清理项");
            return;
        }
        int cleaned = 0;
        for (GeneratedImage img : images) {
            String key = img.getFileKey();
            String bucket = (img.getBucket() != null && !img.getBucket().isBlank())
                    ? img.getBucket() : resultBucket;
            if (key != null && !key.isBlank()) {
                try {
                    minioStorageService.deleteObject(bucket, key);
                } catch (Exception e) {
                    log.warn("[CleanupScheduler] 删除结果图异常 imageId={} bucket={} key={}: {}",
                            img.getId(), bucket, key, e.getMessage());
                }
            }
            // @TableLogic：deleteById 实际执行 UPDATE deleted=1（软删）
            generatedImageMapper.deleteById(img.getId());
            cleaned++;
        }
        log.info("[CleanupScheduler] 结果图到期清理完成 cleanedImages={}/{}", cleaned, images.size());
    }
}
