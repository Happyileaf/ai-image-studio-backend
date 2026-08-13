package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台任务列表视图（全站任务，含用户邮箱 / 风格名）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTaskVO implements Serializable {

    private Long id;

    private String taskNo;

    private Long userId;

    private String userEmail;

    private Long styleId;

    private String styleName;

    private Integer imageCount;

    private String status;

    private Integer successCount;

    private Integer failCount;

    /** 耗时秒（finishedAt - startedAt，未完成则为 null） */
    private Long durationSeconds;

    private LocalDateTime createdAt;
}
