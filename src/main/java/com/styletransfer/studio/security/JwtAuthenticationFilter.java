package com.styletransfer.studio.security;

import com.styletransfer.studio.common.constant.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * <p>从 Authorization: Bearer {token} 提取 token → 解析校验 → 查 Redis 黑名单 →
 * 构建 Authentication 放入 SecurityContext + 设置 UserContextHolder。
 * 无效/过期/黑名单不设置 SecurityContext，由后续 ExceptionTranslation 处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** 白名单路径（与 SecurityConfig permitAll 保持一致） */
    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/send-code",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/info",
            "/health",
            "/",
            "/error"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if (!isWhitelisted(path)) {
                String token = resolveToken(request);
                if (StringUtils.hasText(token)) {
                    LoginUser loginUser = jwtUtils.parseAndValidate(token);
                    if (loginUser != null && !isBlacklisted(token)) {
                        authenticate(loginUser);
                    } else {
                        log.debug("[JwtFilter] token 无效/过期/黑名单，跳过认证: {}", path);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 清理 ThreadLocal，避免线程复用导致用户串号
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isWhitelisted(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : WHITELIST) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean isBlacklisted(String token) {
        String key = String.format(Constants.REDIS_KEY_TOKEN_BLACKLIST, token);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    private void authenticate(LoginUser loginUser) {
        String role = loginUser.role();
        String authority = "ROLE_" + (StringUtils.hasText(role) ? role : "USER");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser, null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserContextHolder.set(loginUser);
    }
}
