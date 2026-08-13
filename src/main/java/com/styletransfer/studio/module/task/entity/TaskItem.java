package com.styletransfer.studio.module.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务项实体（对应 task_item 表，每张原图一项）
 */
@Data
@TableName("task_item")
public class TaskItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** 冗余用户 ID（便于清理） */
    private Long userId;

    /** 任务内序号（1~9） */
    private Integer seq;

    /** 原图临时 key（清理后置空） */
    private String sourceFileKey;

    private String sourceBucket;

    /** 结果图 key（成功后写入） */
    private String resultFileKey;

    private String resultBucket;

    /** PENDING/PROCESSING/SUCCESS/FAILED/CANCELED */
    private String status;

    private String errorCode;

    private String errorMsg;

    private Integer retryCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
