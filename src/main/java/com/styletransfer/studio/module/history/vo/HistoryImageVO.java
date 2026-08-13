package com.styletransfer.studio.module.history.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 历史生成图片视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryImageVO implements Serializable {

    private Long id;

    private String styleName;

    /** 预签名 GET URL（1h 有效） */
    private String url;

    private Long size;

    private Integer width;

    private Integer height;

    private LocalDateTime createdAt;

    /** 剩余保留天数（>0） */
    private Integer remainDays;
}
