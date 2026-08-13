package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台风格视图（含 promptTemplate / negativePrompt / 时间戳）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStyleVO implements Serializable {

    private Long id;

    private String name;

    private String category;

    /** 预签名封面 URL（service 生成） */
    private String coverUrl;

    private String promptTemplate;

    private String negativePrompt;

    private Integer sortOrder;

    /** 0 下架 1 上架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
