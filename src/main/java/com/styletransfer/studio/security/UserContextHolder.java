package com.styletransfer.studio.security;

import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;

/**
 * 基于 ThreadLocal 的当前登录用户上下文
 *
 * <p>由 {@code JwtAuthenticationFilter} 在请求进入时设置，请求结束时清理。</p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户，取不到抛 {@link BizException}(NOT_LOGIN)
     */
    public static LoginUser getRequired() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.NOT_LOGIN);
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
