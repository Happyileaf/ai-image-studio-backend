package com.styletransfer.studio.module.task.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务返回视图（列表 / 当前任务 / 创建结果）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskVO implements Serializable {

    private Long taskId;

    private String taskNo;

    private String status;

    private Integer imageCount;

    private Integer successCount;

    private Integer failCount;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
