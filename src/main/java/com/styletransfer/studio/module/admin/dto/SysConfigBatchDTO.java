package com.styletransfer.studio.module.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量更新系统配置请求
 */
@Data
public class SysConfigBatchDTO implements Serializable {

    @NotEmpty(message = "configs 不能为空")
    @Valid
    private List<Item> configs;

    @Data
    public static class Item implements Serializable {
        private String key;
        private String value;
    }
}
