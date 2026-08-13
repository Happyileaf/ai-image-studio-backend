package com.styletransfer.studio.module.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用状态变更请求（用户启用/禁用、风格上下架）
 */
@Data
public class StatusDTO implements Serializable {

    /** 0 禁用/下架；1 启用/上架 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
