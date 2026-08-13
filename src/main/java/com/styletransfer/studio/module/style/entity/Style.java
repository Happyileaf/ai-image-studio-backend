package com.styletransfer.studio.module.style.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 风格实体（对应 style 表）
 */
@Data
@TableName("style")
public class Style implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 风格名 */
    private String name;

    /** 分类：PAINTING / PHOTO / ART */
    private String category;

    /** 封面图 key */
    @TableField("cover_key")
    private String coverKey;

    /** 提示词模板 */
    private String promptTemplate;

    /** 负向提示词 */
    @TableField("negative_prompt")
    private String negativePrompt;

    /** 排序（越小越前） */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 0 下架 1 上架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
