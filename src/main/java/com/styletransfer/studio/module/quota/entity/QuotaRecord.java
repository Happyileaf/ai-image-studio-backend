package com.styletransfer.studio.module.quota.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 额度变更记录实体（对应 quota_record 表）
 */
@Data
@TableName("quota_record")
public class QuotaRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 变化量（正为增加，负为扣减） */
    private Integer delta;

    /** 变更后余额（快照） */
    private Integer balance;

    /** TASK_CREATE / TASK_FAIL_REFUND / ADMIN_ADJUST */
    private String reason;

    private Long taskId;

    private Long taskItemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
