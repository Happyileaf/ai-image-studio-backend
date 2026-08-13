package com.styletransfer.studio.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 后台创建/编辑风格请求（JSON 部分，封面图通过 multipart file 上传）
 */
@Data
public class AdminStyleEditDTO implements Serializable {

    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 最长 64 字符")
    private String name;

    @NotBlank(message = "category 不能为空")
    @Size(max = 32, message = "category 最长 32 字符")
    private String category;

    @NotBlank(message = "promptTemplate 不能为空")
    private String promptTemplate;

    @Size(max = 500, message = "negativePrompt 最长 500 字符")
    private String negativePrompt;

    private Integer sortOrder;

    /** 0 下架 1 上架；可选，默认 0 */
    private Integer status;
}
