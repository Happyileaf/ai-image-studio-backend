package com.styletransfer.studio.common.aspect;

import com.styletransfer.studio.common.annotation.RateLimit;
import com.styletransfer.studio.common.constant.Constants;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.security.LoginUser;
import com.styletransfer.studio.security.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.UUID;

/**
 * 限流切面：Redis ZSET 滑动窗口（Lua 原子操作）
 *
 * <p>未登录用户按 IP 限流；登录用户按 userId 限流。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        // KEYS[1]=限流 key, ARGV[1]=nowMillis, ARGV[2]=windowSeconds, ARGV[3]=limit, ARGV[4]=member(unique)
        String lua = """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local windowMs = tonumber(ARGV[2]) * 1000
                local limit = tonumber(ARGV[3])
                local member = ARGV[4]
                redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)
                local count = redis.call('ZCARD', key)
                if count >= limit then
                  return 0
                end
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, windowMs)
                return 1
                """;
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setScriptText(lua);
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String identity = resolveIdentity();
        String key = String.format(Constants.REDIS_KEY_RATE_LIMIT, rateLimit.key(), identity);

        long now = System.currentTimeMillis();
        Long allowed = stringRedisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(rateLimit.window()),
                String.valueOf(rateLimit.limit()),
                UUID.randomUUID().toString()
        );

        if (allowed == null || allowed == 0L) {
            throw new BizException(ResultCode.RATE_LIMITED);
        }
        return pjp.proceed();
    }

    /**
     * 解析限流主体：登录用户用 userId，未登录用客户端 IP
     */
    private String resolveIdentity() {
        LoginUser user = UserContextHolder.get();
        if (user != null && user.userId() != null) {
            return "u" + user.userId();
        }
        return "ip_" + getClientIp();
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "unknown";
            }
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                int comma = ip.indexOf(',');
                return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank()) {
                return ip.trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
