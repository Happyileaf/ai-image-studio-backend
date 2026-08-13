package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台用户列表视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserVO implements Serializable {

    private Long id;

    private String email;

    private String nickname;

    private Integer quota;

    private String role;

    /** 0 禁用 1 正常 */
    private Integer status;

    private LocalDateTime createdAt;
}
