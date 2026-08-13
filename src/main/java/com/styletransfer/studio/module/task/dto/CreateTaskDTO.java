package com.styletransfer.studio.module.task.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建任务请求 DTO
 */
@Data
public class CreateTaskDTO implements Serializable {

    /** 原图 fileKey 列表（1~9 张） */
    @NotEmpty(message = "fileKeys 不能为空")
    @Size(min = 1, max = 9, message = "图片数量必须 1~9 张")
    private List<String> fileKeys;

    /** 风格 ID */
    @NotNull(message = "styleId 不能为空")
    private Long styleId;

    /** 用户自定义提示词（可空，最长 500） */
    @Size(max = 500, message = "自定义提示词最长 500 字符")
    private String customPrompt;
}
