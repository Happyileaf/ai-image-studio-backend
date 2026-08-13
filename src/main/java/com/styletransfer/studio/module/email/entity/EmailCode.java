package com.styletransfer.studio.module.email.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邮箱验证码实体（对应 email_code 表，审计用途）
 */
@Data
@TableName("email_code")
public class EmailCode implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    /** 6 位验证码 */
    private String code;

    /** 用途：REGISTER / RESET_PASSWORD */
    private String purpose;

    /** 过期时间（10 分钟） */
    private LocalDateTime expireAt;

    /** 是否已使用 0 否 1 是 */
    private Integer used;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
