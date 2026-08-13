package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 额度变更流水视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaRecordVO implements Serializable {

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

    private LocalDateTime createdAt;
}
