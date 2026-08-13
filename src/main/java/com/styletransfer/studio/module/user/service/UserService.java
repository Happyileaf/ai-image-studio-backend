package com.styletransfer.studio.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.styletransfer.studio.common.constant.Constants;
import com.styletransfer.studio.common.enums.UserRole;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.module.email.service.EmailService;
import com.styletransfer.studio.module.user.dto.ChangePasswordDTO;
import com.styletransfer.studio.module.user.dto.LoginDTO;
import com.styletransfer.studio.module.user.dto.RefreshDTO;
import com.styletransfer.studio.module.user.dto.RegisterDTO;
import com.styletransfer.studio.module.user.entity.User;
import com.styletransfer.studio.module.user.mapper.UserMapper;
import com.styletransfer.studio.module.user.vo.LoginVO;
import com.styletransfer.studio.module.user.vo.UserVO;
import com.styletransfer.studio.security.JwtUtils;
import com.styletransfer.studio.security.LoginUser;
import com.styletransfer.studio.security.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 用户服务：注册 / 登录 / 刷新 / 登出 / 当前用户 / 改密
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    @Value("${app.quota.default:20}")
    private Integer defaultQuota;

    private final UserMapper userMapper;
    private final EmailService emailService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 注册：校验邮箱未注册 → 校验验证码 → BCrypt 加密 → 建用户 → 签发令牌
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        // 1. 邮箱是否已注册
        User exist = selectByEmail(dto.getEmail());
        if (exist != null) {
            throw new BizException(ResultCode.EMAIL_REGISTERED);
        }

        // 2. 校验验证码（成功后标记 used + 删 Redis）
        emailService.verify(dto.getEmail(), "REGISTER", dto.getCode());

        // 3. 建用户
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRole(UserRole.USER.name());
        user.setQuota(defaultQuota);
        user.setStatus(1);
        userMapper.insert(user);

        log.info("[UserService] 用户注册成功 id={} email={}", user.getId(), user.getEmail());
        return issueTokens(user);
    }

    /**
     * 登录：查用户 → 校验密码 → 禁用判断 → 签发令牌
     */
    public LoginVO login(LoginDTO dto) {
        User user = selectByEmail(dto.getEmail());
        if (user == null) {
            throw new BizException(ResultCode.EMAIL_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.PASSWORD_INCORRECT);
        }
        assertEnabled(user);
        return issueTokens(user);
    }

    /**
     * 刷新令牌：校验 refresh token → 加载用户 → 签发新 access
     */
    public LoginVO refresh(RefreshDTO dto) {
        String refreshToken = dto.getRefreshToken();
        LoginUser loginUser = jwtUtils.parseAndValidate(refreshToken);
        if (loginUser == null || loginUser.tokenType() != LoginUser.TokenType.REFRESH) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        if (isBlacklisted(refreshToken)) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        User user = userMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_LOGIN);
        }
        assertEnabled(user);

        // 仅签发新 access，refresh 不轮换
        String newAccess = jwtUtils.generateAccessToken(toLoginUser(user, LoginUser.TokenType.ACCESS));
        return LoginVO.builder()
                .accessToken(newAccess)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getAccessTokenTtlSeconds())
                .build();
    }

    /**
     * 登出：将 access token 加入 Redis 黑名单（TTL = 剩余有效期）
     */
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        long remaining = jwtUtils.getRemainingTtlSeconds(accessToken);
        if (remaining <= 0) {
            return;
        }
        String key = String.format(Constants.REDIS_KEY_TOKEN_BLACKLIST, accessToken);
        stringRedisTemplate.opsForValue().set(key, "1", remaining, TimeUnit.SECONDS);
        log.info("[UserService] token 已加入黑名单，剩余有效期 {}s", remaining);
    }

    /**
     * 当前登录用户信息
     */
    public UserVO getCurrentUser() {
        LoginUser ctx = UserContextHolder.getRequired();
        User user = userMapper.selectById(ctx.userId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_LOGIN);
        }
        return toUserVO(user);
    }

    /**
     * 修改密码：校验原密码 → BCrypt 新密码 → 更新
     */
    public void changePassword(ChangePasswordDTO dto) {
        LoginUser ctx = UserContextHolder.getRequired();
        User user = userMapper.selectById(ctx.userId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_LOGIN);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.PASSWORD_INCORRECT);
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("[UserService] 用户修改密码成功 id={}", user.getId());
    }

    // ===== 私有方法 =====

    private User selectByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .last("LIMIT 1"));
    }

    private void assertEnabled(User user) {
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.USER_DISABLED);
        }
    }

    private boolean isBlacklisted(String token) {
        String key = String.format(Constants.REDIS_KEY_TOKEN_BLACKLIST, token);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    private LoginVO issueTokens(User user) {
        LoginUser loginUser = toLoginUser(user, LoginUser.TokenType.ACCESS);
        String access = jwtUtils.generateAccessToken(loginUser);
        String refresh = jwtUtils.generateRefreshToken(toLoginUser(user, LoginUser.TokenType.REFRESH));
        return LoginVO.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(jwtUtils.getAccessTokenTtlSeconds())
                .build();
    }

    private LoginUser toLoginUser(User user, LoginUser.TokenType type) {
        return new LoginUser(user.getId(), user.getEmail(), user.getRole(), type);
    }

    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .quota(user.getQuota())
                .status(user.getStatus())
                .build();
    }
}
