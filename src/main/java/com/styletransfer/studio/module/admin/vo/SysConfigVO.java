package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 系统配置视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysConfigVO implements Serializable {

    private String key;

    private String value;

    private String description;
}
