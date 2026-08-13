package com.styletransfer.studio.module.style.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 风格返回视图
 *
 * <p>列表仅返回 id/name/category/coverUrl/sortOrder/status；
 * 详情额外返回 promptTemplate。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleVO implements Serializable {

    private Long id;
    private String name;
    private String category;

    /** 预签名封面 URL（service 生成，有效期 1 小时） */
    private String coverUrl;

    /** 提示词模板（仅详情返回） */
    private String promptTemplate;

    private Integer sortOrder;
    private Integer status;
}
