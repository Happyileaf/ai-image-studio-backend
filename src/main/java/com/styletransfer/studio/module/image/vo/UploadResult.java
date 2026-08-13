package com.styletransfer.studio.module.image.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片上传返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult implements Serializable {

    /** 原图对象 key（后续创建任务时回传） */
    private String fileKey;

    /** 原图预签名预览 URL（短期有效） */
    private String previewUrl;
}
