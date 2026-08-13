package com.styletransfer.studio.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应码枚举
 *
 * <p>范围约定：
 * <ul>
 *   <li>0     - 成功</li>
 *   <li>1xxx  - 通用（参数/鉴权/限流）</li>
 *   <li>2xxx  - 用户模块</li>
 *   <li>3xxx  - 图片模块</li>
 *   <li>4xxx  - 风格模块</li>
 *   <li>5xxx  - 任务模块</li>
 *   <li>6xxx  - 额度模块</li>
 *   <li>9xxx  - 系统</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ===== 成功 =====
    SUCCESS(0, "ok"),

    // ===== 1xxx 通用 =====
    PARAM_INVALID(1001, "参数校验失败"),
    NOT_LOGIN(1003, "未登录或登录已过期"),
    NO_PERMISSION(1004, "无权限访问"),
    RATE_LIMITED(1005, "请求过于频繁，请稍后再试"),
    TOKEN_INVALID(1006, "登录凭证无效"),
    REFRESH_TOKEN_INVALID(1007, "刷新凭证无效或已过期"),

    // ===== 2xxx 用户模块 =====
    EMAIL_REGISTERED(2001, "邮箱已被注册"),
    CODE_INVALID(2002, "验证码错误"),
    CODE_EXPIRED(2003, "验证码已过期"),
    EMAIL_NOT_FOUND(2004, "邮箱未注册"),
    PASSWORD_INCORRECT(2005, "密码错误"),
    USER_DISABLED(2006, "账号已被禁用"),

    // ===== 3xxx 图片模块 =====
    IMAGE_TYPE_UNSUPPORTED(3001, "图片格式不支持，仅支持 JPG/PNG/WebP"),
    IMAGE_TOO_LARGE(3002, "图片大小超限"),
    IMAGE_ILLEGAL(3003, "图片非法（文件类型不匹配）"),
    IMAGE_RESOLUTION_TOO_LARGE(3004, "图片分辨率超限（≤4096×4096）"),
    IMAGE_NOT_FOUND(3005, "图片不存在"),

    // ===== 4xxx 风格模块 =====
    STYLE_NOT_FOUND(4001, "风格不存在"),
    STYLE_OFFLINE(4002, "风格已下架"),

    // ===== 5xxx 任务模块 =====
    TASK_NOT_FOUND(5001, "任务不存在"),
    TASK_STATUS_INVALID(5002, "任务状态不允许该操作"),
    AI_TIMEOUT(5003, "AI 调用超时"),
    CONTENT_VIOLATION(5004, "内容违规，无法生成"),
    TASK_IN_PROGRESS(5005, "已有任务进行中，请等待完成"),
    IMAGE_COUNT_INVALID(5006, "图片数量不合法（必须 1~9 张）"),
    TASK_LOCK_FAIL(5007, "任务锁获取失败，请稍后重试"),
    TASK_CANCEL_NOT_ALLOWED(5008, "处理中无法取消，请等待或稍后重试"),

    // ===== 6xxx 额度模块 =====
    QUOTA_NOT_ENOUGH(6001, "额度不足"),

    // ===== 9xxx 系统 =====
    SYSTEM_ERROR(9001, "系统内部错误"),
    SERVICE_UNAVAILABLE(9002, "服务暂不可用");

    private final Integer code;
    private final String message;
}
