package com.styletransfer.studio.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求
 */
@Data
public class SendCodeDTO implements Serializable {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 用途：REGISTER / RESET_PASSWORD */
    @NotBlank(message = "用途不能为空")
    @Pattern(regexp = "REGISTER|RESET_PASSWORD", message = "用途仅支持 REGISTER / RESET_PASSWORD")
    private String purpose;
}
