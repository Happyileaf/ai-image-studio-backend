package com.styletransfer.studio.module.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员调整用户额度请求
 */
@Data
public class QuotaAdjustDTO implements Serializable {

    /** 变化量：正为增加，负为扣减 */
    @NotNull(message = "delta 不能为空")
    private Integer delta;

    /** 调整原因（可选，记录到流水） */
    private String reason;
}
