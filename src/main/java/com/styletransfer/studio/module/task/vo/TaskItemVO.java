package com.styletransfer.studio.module.task.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务项返回视图
 *
 * <p>隐私/安全：不含 sourceFileKey / resultFileKey。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskItemVO implements Serializable {

    private Long id;

    private Integer seq;

    private String status;

    private String errorCode;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
