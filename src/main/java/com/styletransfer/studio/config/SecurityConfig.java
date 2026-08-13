package com.styletransfer.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置（骨架）
 *
 * <p>JWT 无状态鉴权：SessionCreationPolicy.STATELESS</p>
 * <p>完整的 JwtAuthenticationFilter 将在 M1（用户系统）中接入，这里仅放行所有接口确保骨架可启动。</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * 密码加密器：BCrypt（cost=10）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 初始化安全过滤链（MVP 骨架阶段：仅启用安全框架默认配置，不对接口进行拦截）。
     * M1 用户系统实现后，这里会添加 JwtAuthenticationFilter、路由权限控制等。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // ===== MVP 骨架启动：临时禁用 CSRF + 放行所有请求 =====
        // TODO(M1): 替换为真实的鉴权过滤链：
        //   1. 添加 JwtAuthenticationFilter 到 UsernamePasswordAuthenticationFilter 之前
        //   2. 放行 /auth/**（登录、注册、发验证码）与 Swagger、Actuator health
        //   3. /api/** 其余需要登录
        //   4. /admin/** 需要 @PreAuthorize("hasRole('ADMIN')") 方法级控制
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
