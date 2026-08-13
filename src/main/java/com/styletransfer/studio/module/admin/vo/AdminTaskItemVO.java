package com.styletransfer.studio.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 后台任务项明细视图
 *
 * <p>隐私红线：严禁包含 sourceFileKey / resultFileKey（仅返回状态 / 错误信息 / 重试次数）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTaskItemVO implements Serializable {

    private Long id;

    private Integer seq;

    private String status;

    private String errorCode;

    private String errorMsg;

    private Integer retryCount;
}
