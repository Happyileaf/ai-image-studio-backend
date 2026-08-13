package com.styletransfer.studio.module.history.entity;

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
 * 用户生成图片实体（对应 generated_image 表，历史维度 / 用户作品集）
 *
 * <p>结果图成功生成后由 Worker 写入；按 {@code created_at + 保留天数} 计算过期清理（无 expire_at 字段）。</p>
 */
@Data
@TableName("generated_image")
public class GeneratedImage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long taskId;

    private Long taskItemId;

    private Long styleId;

    /** 风格名快照（冗余，列表展示用） */
    private String styleName;

    private String fileKey;

    private String bucket;

    private Integer width;

    private Integer height;

    private Long size;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除 0 未删除 1 已删除 */
    @TableLogic
    private Integer deleted;
}
