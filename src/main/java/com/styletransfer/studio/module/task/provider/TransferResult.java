package com.styletransfer.studio.module.task.provider;

/**
 * 风格迁移结果（适配层出参）
 *
 * @param resultImageUrl  成功时结果图 URL（供 Worker 拉取后上传到结果桶）；失败时为 null
 * @param moderationFlag  内容审核标记：NONE / REJECTED
 * @param errorCode       失败错误码：AI_TIMEOUT / INTERNAL / CONTENT_VIOLATION；成功为 null
 * @param success         是否成功
 */
public record TransferResult(
        String resultImageUrl,
        String moderationFlag,
        String errorCode,
        boolean success
) implements java.io.Serializable {

    /** 审核标记常量 */
    public static final String MOD_NONE = "NONE";
    public static final String MOD_REJECTED = "REJECTED";

    /** 错误码常量 */
    public static final String ERR_AI_TIMEOUT = "AI_TIMEOUT";
    public static final String ERR_INTERNAL = "INTERNAL";
    public static final String ERR_CONTENT_VIOLATION = "CONTENT_VIOLATION";

    /**
     * 构造成功结果
     */
    public static TransferResult ok(String resultImageUrl) {
        return new TransferResult(resultImageUrl, MOD_NONE, null, true);
    }

    /**
     * 构造失败结果
     */
    public static TransferResult fail(String errorCode) {
        String mod = ERR_CONTENT_VIOLATION.equals(errorCode) ? MOD_REJECTED : MOD_NONE;
        return new TransferResult(null, mod, errorCode, false);
    }
}
