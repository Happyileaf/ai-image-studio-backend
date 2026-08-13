package com.styletransfer.studio.common.enums;

/**
 * 任务状态枚举
 */
public enum TaskStatus {
    /** 待处理：任务已创建，等待 Worker 拉取 */
    PENDING,
    /** 处理中：Worker 正在调用 AI 模型 */
    PROCESSING,
    /** 成功：所有图片处理成功，结果图已生成（终态） */
    SUCCESS,
    /** 失败：存在图片处理失败且重试耗尽（终态，可重试） */
    FAILED,
    /** 已取消：用户主动取消（终态） */
    CANCELED
}
