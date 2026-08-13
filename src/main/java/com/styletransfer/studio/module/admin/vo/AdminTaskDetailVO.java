package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台任务详情视图
 *
 * <p>包含任务基本信息 + 子项明细（{@link AdminTaskItemVO}，不含 fileKey）
 * + 该任务关联的额度流水（{@link QuotaRecordVO}）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTaskDetailVO implements Serializable {

    private Long id;

    private String taskNo;

    private Long userId;

    private String userEmail;

    private Long styleId;

    private String styleName;

    private Integer imageCount;

    private String customPrompt;

    private String status;

    private Integer successCount;

    private Integer failCount;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 子项明细（不含 fileKey） */
    private List<AdminTaskItemVO> items;

    /** 关联额度流水 */
    private List<QuotaRecordVO> quotaRecords;
}
