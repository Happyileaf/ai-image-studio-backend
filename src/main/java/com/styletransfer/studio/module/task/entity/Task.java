package com.styletransfer.studio.module.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 风格迁移任务实体（对应 task 表）
 */
@Data
@TableName("task")
public class Task implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（用户可读） */
    private String taskNo;

    private Long userId;

    private Long styleId;

    /** 图片张数（1~9） */
    private Integer imageCount;

    /** 用户自定义提示词 */
    private String customPrompt;

    /** PENDING/PROCESSING/SUCCESS/FAILED/CANCELED */
    private String status;

    private Integer successCount;

    private Integer failCount;

    /** 失败原因汇总 / 取消请求标记（CANCEL_REQUESTED） */
    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 原图是否已清理 0 否 1 是 */
    private Integer originalCleaned;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除 0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
