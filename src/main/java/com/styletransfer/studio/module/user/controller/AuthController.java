package com.styletransfer.studio.module.user.controller;

import com.styletransfer.studio.common.annotation.RateLimit;
import com.styletransfer.studio.common.result.Result;
import com.styletransfer.studio.module.email.service.EmailService;
import com.styletransfer.studio.module.user.dto.LoginDTO;
import com.styletransfer.studio.module.user.dto.RefreshDTO;
import com.styletransfer.studio.module.user.dto.RegisterDTO;
import com.styletransfer.studio.module.user.dto.SendCodeDTO;
import com.styletransfer.studio.module.user.service.UserService;
import com.styletransfer.studio.module.user.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 鉴权接口：发送验证码 / 注册 / 登录 / 刷新 / 登出
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final EmailService emailService;

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/send-code")
    @RateLimit(key = "send-code", limit = 1, window = 60)
    public Result<Map<String, Long>> sendCode(@Valid @RequestBody SendCodeDTO dto) {
        long expiresIn = emailService.generateAndSend(dto.getEmail(), dto.getPurpose());
        return Result.success(Map.of("expires_in", expiresIn));
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.success(userService.refresh(dto));
    }

    /**
     * 登出：将 access token 加入黑名单
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                               HttpServletRequest request) {
        String token = resolveToken(authHeader, request);
        userService.logout(token);
        return Result.success();
    }

    private String resolveToken(String authHeader, HttpServletRequest request) {
        String header = authHeader;
        if (header == null || header.isBlank()) {
            header = request.getHeader("Authorization");
        }
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
