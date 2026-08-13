package com.styletransfer.studio.module.task.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务结果图视图（仅结果图，不含原图信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultImageVO implements Serializable {

    private Long itemId;

    private Integer seq;

    /** 结果图 fileKey（可选不返回，默认不填充） */
    private String fileKey;

    /** 预签名 GET URL（1h 有效） */
    private String url;

    private Long size;

    private Integer width;

    private Integer height;
}
