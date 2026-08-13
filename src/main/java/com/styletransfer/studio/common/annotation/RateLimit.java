package com.styletransfer.studio.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（基于 Redis ZSET 滑动窗口）
 *
 * <p>key 维度：rate_limit:{key}:{userId或ip}。未登录用户按 IP 限流。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流业务 key */
    String key();

    /** 窗口内允许的最大请求数 */
    int limit();

    /** 时间窗口（秒） */
    int window();
}
