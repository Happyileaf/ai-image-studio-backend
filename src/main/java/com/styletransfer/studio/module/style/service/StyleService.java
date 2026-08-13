package com.styletransfer.studio.module.style.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.module.style.entity.Style;
import com.styletransfer.studio.module.style.mapper.StyleMapper;
import com.styletransfer.studio.module.style.vo.StyleVO;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 风格服务：上架列表 / 详情
 *
 * <p>封面图预签名 URL 直接通过 {@link MinioClient} 生成（不依赖图片模块的
 * MinioStorageService，避免与并行模块耦合），bucket 名读取自
 * {@code minio.buckets.style}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleService {

    private final StyleMapper styleMapper;
    private final MinioClient minioClient;

    @Value("${minio.buckets.style:styles-cover}")
    private String styleBucket;

    /**
     * 上架风格列表：status=1，按 sortOrder asc；category 非空则加条件。
     *
     * @param category 分类（PAINTING/PHOTO/ART），可空
     * @return 上架风格列表
     */
    public List<StyleVO> listOnShelf(String category) {
        LambdaQueryWrapper<Style> wrapper = new LambdaQueryWrapper<Style>()
                .eq(Style::getStatus, 1)
                .eq(category != null && !category.isBlank(), Style::getCategory, category)
                .orderByAsc(Style::getSortOrder);
        List<Style> styles = styleMapper.selectList(wrapper);
        return styles.stream().map(this::toListVO).toList();
    }

    /**
     * 风格详情：不存在 → STYLE_NOT_FOUND；下架也返回（前台/后台共用）。
     *
     * @param id 风格 ID
     * @return 风格详情
     */
    public StyleVO getDetail(Long id) {
        Style style = styleMapper.selectById(id);
        if (style == null) {
            throw new BizException(ResultCode.STYLE_NOT_FOUND);
        }
        return toDetailVO(style);
    }

    // ===== 私有方法 =====

    private StyleVO toListVO(Style style) {
        return StyleVO.builder()
                .id(style.getId())
                .name(style.getName())
                .category(style.getCategory())
                .coverUrl(presignedCoverUrl(style.getCoverKey()))
                .sortOrder(style.getSortOrder())
                .status(style.getStatus())
                .build();
    }

    private StyleVO toDetailVO(Style style) {
        return StyleVO.builder()
                .id(style.getId())
                .name(style.getName())
                .category(style.getCategory())
                .coverUrl(presignedCoverUrl(style.getCoverKey()))
                .promptTemplate(style.getPromptTemplate())
                .sortOrder(style.getSortOrder())
                .status(style.getStatus())
                .build();
    }

    /**
     * 生成封面图预签名 URL（有效期 1 小时）；coverKey 为空返回 null。
     */
    private String presignedCoverUrl(String coverKey) {
        if (coverKey == null || coverKey.isBlank()) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(styleBucket)
                            .object(coverKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.warn("[StyleService] 生成封面预签名 URL 失败 bucket={} key={}: {}",
                    styleBucket, coverKey, e.getMessage());
            return null;
        }
    }
}
