package com.styletransfer.studio.infra.ai;

import com.styletransfer.studio.module.task.provider.ImageStyleTransferProvider;
import com.styletransfer.studio.module.task.provider.TransferRequest;
import com.styletransfer.studio.module.task.provider.TransferResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 风格迁移桩实现（不调真实 AI API）
 *
 * <p>策略：直接复用原图作为结果图（resultImageUrl = sourceImageUrl）。
 * Worker 从该 URL 拉取字节（即原图）上传到 images-result 桶，闭环可跑通且不需要真实 AI。</p>
 *
 * <p>当 {@code ai.provider=stub}（或缺省）时注册为默认实现。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubImageStyleTransferProvider implements ImageStyleTransferProvider {

    @Override
    public TransferResult transfer(TransferRequest request) {
        log.debug("[StubProvider] 桩实现直接复用原图作为结果图 sourceUrl={} strength={}",
                request.sourceImageUrl(), request.strength());
        // 稳定成功：结果图 = 原图 URL，审核标记 NONE
        return TransferResult.ok(request.sourceImageUrl());
    }
}
