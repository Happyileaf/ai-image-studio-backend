package com.styletransfer.studio.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析 / 校验
 *
 * <p>使用 jjwt-gson（避免 Jackson 3 冲突）。
 * 签名算法由 {@link Keys#hmacShaKeyFor} 根据密钥长度自动选择（secret >= 64 字节时为 HS512）。</p>
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-ttl}")
    private Duration accessTokenTtl;

    @Value("${jwt.refresh-token-ttl}")
    private Duration refreshTokenTtl;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret 必须至少 32 字节（建议 64 字节以上）");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发访问令牌（TTL = access-token-ttl，默认 2h）
     */
    public String generateAccessToken(LoginUser user) {
        return buildToken(user, LoginUser.TokenType.ACCESS, accessTokenTtl);
    }

    /**
     * 签发刷新令牌（TTL = refresh-token-ttl，默认 7d），claim type=REFRESH
     */
    public String generateRefreshToken(LoginUser user) {
        return buildToken(user, LoginUser.TokenType.REFRESH, refreshTokenTtl);
    }

    private String buildToken(LoginUser user, LoginUser.TokenType type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("email", user.email())
                .claim("role", user.role())
                .claim("type", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，签名错误 / 过期 / 格式非法返回 null
     */
    public LoginUser parseAndValidate(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims claims = jws.getPayload();
            String type = claims.get("type", String.class);
            LoginUser.TokenType tokenType = (type != null) ? LoginUser.TokenType.valueOf(type) : LoginUser.TokenType.ACCESS;
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    tokenType
            );
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取 token 剩余有效期（秒），过期或非法返回 0
     */
    public long getRemainingTtlSeconds(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            long remaining = claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(remaining, 0L);
        } catch (JwtException | IllegalArgumentException e) {
            return 0L;
        }
    }

    /**
     * 访问令牌有效期（秒）
     */
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
