package com.styletransfer.studio.module.task.provider;

/**
 * 风格迁移请求（适配层入参）
 *
 * <p>由任务项处理器构造，交给 {@link ImageStyleTransferProvider} 执行风格迁移。</p>
 *
 * @param sourceImageUrl  原图预签名 URL（供 AI 读取）
 * @param prompt          正向提示词（已渲染最终文本）
 * @param negativePrompt  负向提示词（可为空）
 * @param strength        风格强度（0~100，默认 65）
 */
public record TransferRequest(
        String sourceImageUrl,
        String prompt,
        String negativePrompt,
        int strength
) implements java.io.Serializable {
}
