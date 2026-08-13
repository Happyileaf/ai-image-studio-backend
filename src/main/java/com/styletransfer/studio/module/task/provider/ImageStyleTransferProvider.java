package com.styletransfer.studio.module.task.provider;

/**
 * 风格迁移 AI 适配层接口
 *
 * <p>不同供应商（stub / stability / replicate 等）实现该接口，
 * 由 {@code ai.provider} 配置决定注入哪个实现。</p>
 */
public interface ImageStyleTransferProvider {

    /**
     * 执行风格迁移
     *
     * @param request 请求参数
     * @return 迁移结果（成功包含结果图 URL，失败包含错误码）
     */
    TransferResult transfer(TransferRequest request);
}
