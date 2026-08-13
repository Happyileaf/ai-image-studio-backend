package com.styletransfer.studio.module.task.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务详情视图（含任务项列表，不含 fileKey）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailVO implements Serializable {

    private Long taskId;

    private String taskNo;

    private String status;

    private Long styleId;

    private Integer imageCount;

    private Integer successCount;

    private Integer failCount;

    private String customPrompt;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private List<TaskItemVO> items;
}
