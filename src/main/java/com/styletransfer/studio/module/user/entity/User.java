package com.styletransfer.studio.module.user.entity;

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
 * 用户实体（对应 user 表）
 */
@Data
@TableName("user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邮箱（登录凭证） */
    private String email;

    /** BCrypt 加密密码 */
    @TableField("password_hash")
    private String passwordHash;

    /** 昵称（可选） */
    private String nickname;

    /** 角色：USER / ADMIN */
    private String role;

    /** 当前剩余额度（按张计） */
    private Integer quota;

    /** 0 禁用 1 正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除 0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
