package com.styletransfer.studio.security;

/**
 * 当前登录用户上下文对象（JWT 解析结果）
 *
 * @param userId    用户 ID
 * @param email     邮箱
 * @param role      角色（USER / ADMIN）
 * @param tokenType 令牌类型（ACCESS / REFRESH）
 */
public record LoginUser(Long userId, String email, String role, TokenType tokenType) {

    /** 令牌类型 */
    public enum TokenType {
        /** 访问令牌 */
        ACCESS,
        /** 刷新令牌 */
        REFRESH
    }
}
