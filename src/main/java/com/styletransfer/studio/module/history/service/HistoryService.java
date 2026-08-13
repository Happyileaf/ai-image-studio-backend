package com.styletransfer.studio.module.history.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.history.entity.GeneratedImage;
import com.styletransfer.studio.module.history.mapper.GeneratedImageMapper;
import com.styletransfer.studio.module.history.vo.HistoryImageVO;
import com.styletransfer.studio.security.UserContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 历史记录服务：列表（分页）/ 删除 / 批量删除 / ZIP 下载
 *
 * <p>所有查询加 user_id = 当前用户；仅展示保留期内（remainDays > 0）记录；
 * 预签名 URL 有效期 1 小时。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private static final int URL_EXPIRE_MINUTES = 60;
    private static final DateTimeFormatter ZIP_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final GeneratedImageMapper generatedImageMapper;
    private final MinioStorageService minioStorageService;

    @Value("${app.history.result-image-retention-days:7}")
    private int retentionDays;

    /**
     * 分页查询当前用户历史结果图（仅保留期内 remainDays > 0）。
     */
    public Page<HistoryImageVO> list(int page, int size) {
        Long userId = UserContextHolder.getRequired().userId();
        // 仅保留期内：createdAt > now - retentionDays（保证 remainDays > 0）
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        Page<GeneratedImage> p = generatedImageMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<GeneratedImage>()
                        .eq(GeneratedImage::getUserId, userId)
                        .gt(GeneratedImage::getCreatedAt, cutoff)
                        .orderByDesc(GeneratedImage::getCreatedAt));

        Page<HistoryImageVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    private HistoryImageVO toVO(GeneratedImage gi) {
        String url = minioStorageService.presignedGetUrl(gi.getBucket(), gi.getFileKey(), URL_EXPIRE_MINUTES);
        long days = ChronoUnit.DAYS.between(gi.getCreatedAt().toLocalDate(), LocalDate.now());
        int remainDays = (int) (retentionDays - days);
        return HistoryImageVO.builder()
                .id(gi.getId())
                .styleName(gi.getStyleName())
                .url(url)
                .size(gi.getSize())
                .width(gi.getWidth())
                .height(gi.getHeight())
                .createdAt(gi.getCreatedAt())
                .remainDays(remainDays)
                .build();
    }

    /**
     * 删除单条：校验归属；软删 deleted=1；删除 MinIO 对象（best-effort）。
     */
    public void delete(Long id) {
        Long userId = UserContextHolder.getRequired().userId();
        GeneratedImage gi = generatedImageMapper.selectById(id);
        if (gi == null || !userId.equals(gi.getUserId())) {
            throw new BizException(ResultCode.IMAGE_NOT_FOUND);
        }
        softDeleteAndRemoveObject(gi);
    }

    /**
     * 批量删除：校验归属（仅删当前用户记录）；逐个软删 + 删 MinIO。
     */
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Long userId = UserContextHolder.getRequired().userId();
        List<GeneratedImage> list = generatedImageMapper.selectList(
                new LambdaQueryWrapper<GeneratedImage>()
                        .in(GeneratedImage::getId, ids)
                        .eq(GeneratedImage::getUserId, userId));
        for (GeneratedImage gi : list) {
            softDeleteAndRemoveObject(gi);
        }
    }

    private void softDeleteAndRemoveObject(GeneratedImage gi) {
        generatedImageMapper.update(null, new LambdaUpdateWrapper<GeneratedImage>()
                .eq(GeneratedImage::getId, gi.getId())
                .set(GeneratedImage::getDeleted, 1));
        minioStorageService.deleteObject(gi.getBucket(), gi.getFileKey());
    }

    /**
     * ZIP 流式下载：校验归属；逐个从 MinIO 拉取字节流写入 zip entry。
     * 文件名 history-{yyyyMMddHHmm}.zip；entry 名 style_{styleName}_{id}.png。
     * ids 为空时下载当前用户保留期内全部。
     */
    public void downloadZip(List<Long> ids, HttpServletResponse response) {
        Long userId = UserContextHolder.getRequired().userId();

        List<GeneratedImage> list;
        if (ids == null || ids.isEmpty()) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            list = generatedImageMapper.selectList(
                    new LambdaQueryWrapper<GeneratedImage>()
                            .eq(GeneratedImage::getUserId, userId)
                            .gt(GeneratedImage::getCreatedAt, cutoff)
                            .orderByDesc(GeneratedImage::getCreatedAt));
        } else {
            list = generatedImageMapper.selectList(
                    new LambdaQueryWrapper<GeneratedImage>()
                            .in(GeneratedImage::getId, ids)
                            .eq(GeneratedImage::getUserId, userId)
                            .orderByDesc(GeneratedImage::getCreatedAt));
        }

        String zipName = "history-" + LocalDateTime.now().format(ZIP_TS_FMT) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");

        int downloaded = 0;
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(response.getOutputStream())) {
            for (GeneratedImage gi : list) {
                if (gi.getFileKey() == null) {
                    continue;
                }
                String entryName = "style_" + sanitizeFileName(gi.getStyleName()) + "_" + gi.getId() + ".png";
                ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                zos.putArchiveEntry(entry);
                try (InputStream in = minioStorageService.getObjectStream(gi.getBucket(), gi.getFileKey())) {
                    in.transferTo(zos);
                } catch (Exception e) {
                    log.warn("[HistoryService] zip 下载单文件失败 id={}: {}", gi.getId(), e.getMessage());
                }
                zos.closeArchiveEntry();
                downloaded++;
            }
            zos.finish();
        } catch (Exception e) {
            log.error("[HistoryService] zip 下载失败", e);
        }
        log.info("[HistoryService] zip 下载完成 userId={} count={}", userId, downloaded);
    }

    /** 风格名可能含非法文件名字符，做简单替换 */
    private String sanitizeFileName(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
