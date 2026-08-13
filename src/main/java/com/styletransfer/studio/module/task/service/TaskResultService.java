package com.styletransfer.studio.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.styletransfer.studio.common.enums.TaskItemStatus;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.history.entity.GeneratedImage;
import com.styletransfer.studio.module.history.mapper.GeneratedImageMapper;
import com.styletransfer.studio.module.task.entity.Task;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import com.styletransfer.studio.module.task.mapper.TaskMapper;
import com.styletransfer.studio.module.task.vo.ResultImageVO;
import com.styletransfer.studio.security.UserContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务结果服务：结果图列表 + ZIP 流式下载
 *
 * <p>仅返回成功结果图（task_item.status=SUCCESS），不暴露原图相关字段。
 * 预签名 URL 有效期 1 小时。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskResultService {

    private static final int URL_EXPIRE_MINUTES = 60;
    private static final DateTimeFormatter ZIP_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final GeneratedImageMapper generatedImageMapper;
    private final MinioStorageService minioStorageService;

    /**
     * 任务结果图列表：校验任务归属；查 SUCCESS items；生成预签名 URL(1h)。
     * 尺寸/大小取自 generated_image（按 task_item_id 关联）。
     */
    public List<ResultImageVO> listResults(Long taskId) {
        Long userId = UserContextHolder.getRequired().userId();
        Task task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }

        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .eq(TaskItem::getStatus, TaskItemStatus.SUCCESS.name())
                .orderByAsc(TaskItem::getSeq));

        if (items.isEmpty()) {
            return List.of();
        }

        // 从 generated_image 取尺寸/大小（按 task_item_id 关联）
        List<Long> itemIds = items.stream().map(TaskItem::getId).toList();
        Map<Long, GeneratedImage> dimMap = generatedImageMapper.selectList(
                        new LambdaQueryWrapper<GeneratedImage>()
                                .in(GeneratedImage::getTaskItemId, itemIds))
                .stream()
                .collect(Collectors.toMap(GeneratedImage::getTaskItemId, Function.identity(), (a, b) -> a));

        return items.stream().map(item -> {
            GeneratedImage gi = dimMap.get(item.getId());
            String url = minioStorageService.presignedGetUrl(
                    item.getResultBucket(), item.getResultFileKey(), URL_EXPIRE_MINUTES);
            return ResultImageVO.builder()
                    .itemId(item.getId())
                    .seq(item.getSeq())
                    .url(url)
                    .size(gi != null ? gi.getSize() : null)
                    .width(gi != null ? gi.getWidth() : null)
                    .height(gi != null ? gi.getHeight() : null)
                    .build();
        }).toList();
    }

    /**
     * ZIP 流式下载：校验归属；查 SUCCESS items；逐个从 MinIO 拉取字节流写入 zip entry。
     * 文件名 style-transfer-{yyyyMMddHHmm}.zip；entry 名 style_{seq}_{itemId}.png。
     */
    public void downloadZip(Long taskId, HttpServletResponse response) {
        Long userId = UserContextHolder.getRequired().userId();
        Task task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }

        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .eq(TaskItem::getStatus, TaskItemStatus.SUCCESS.name())
                .orderByAsc(TaskItem::getSeq));

        String zipName = "style-transfer-" + LocalDateTime.now().format(ZIP_TS_FMT) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");

        int downloaded = 0;
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(response.getOutputStream())) {
            for (TaskItem item : items) {
                if (item.getResultFileKey() == null) {
                    continue;
                }
                String entryName = "style_" + item.getSeq() + "_" + item.getId() + ".png";
                ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                zos.putArchiveEntry(entry);
                try (InputStream in = minioStorageService.getObjectStream(
                        item.getResultBucket(), item.getResultFileKey())) {
                    in.transferTo(zos);
                } catch (Exception e) {
                    log.warn("[TaskResultService] zip 下载单文件失败 itemId={}: {}",
                            item.getId(), e.getMessage());
                }
                zos.closeArchiveEntry();
                downloaded++;
            }
            zos.finish();
        } catch (Exception e) {
            log.error("[TaskResultService] zip 下载失败 taskId={}", taskId, e);
        }
        log.info("[TaskResultService] zip 下载完成 taskId={} count={}", taskId, downloaded);
    }
}
