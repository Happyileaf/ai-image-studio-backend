package com.styletransfer.studio.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 刷新令牌请求
 */
@Data
public class RefreshDTO implements Serializable {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
