package com.styletransfer.studio.module.task.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务进度视图（SSE 推送载荷）
 *
 * @param status      任务状态：PROCESSING / SUCCESS / FAILED / CANCELED
 * @param total       图片总张数
 * @param success     已成功张数
 * @param failed      已失败张数
 * @param currentItem 当前处理项序号（1~9，无则 0）
 * @param stage       当前阶段：PROCESSING / SUCCESS / FAILED / CANCELED
 * @param progress    进度百分比（0~100）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressVO implements Serializable {

    private String status;

    private Integer total;

    private Integer success;

    private Integer failed;

    private Integer currentItem;

    private String stage;

    private Integer progress;
}
