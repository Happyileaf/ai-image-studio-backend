package com.styletransfer.studio.common.constant;

/**
 * 全局常量
 */
public final class Constants {

    private Constants() {}

    // ===== API 前缀 =====
    public static final String API_PREFIX = "/api/v1";

    // ===== Redis Key 前缀 =====
    public static final String REDIS_KEY_EMAIL_CODE = "email_code:%s:%s";       // email_code:{email}:{purpose}
    public static final String REDIS_KEY_EMAIL_CODE_RATE = "email_code:rate:%s";          // email_code:rate:{email}（60s 限频）
    public static final String REDIS_KEY_EMAIL_CODE_DAILY = "email_code:daily:%s:%s";     // email_code:daily:{email}:{date}
    public static final String REDIS_KEY_QUOTA = "quota:%d";                      // quota:{userId}
    public static final String REDIS_KEY_TOKEN_BLACKLIST = "token_blacklist:%s";  // token_blacklist:{token}
    public static final String REDIS_KEY_RATE_LIMIT = "rate_limit:%s:%s";         // rate_limit:{key}:{userId或ip}
    public static final String REDIS_KEY_LOCK_USER_TASK = "user:task:lock:%d";    // user:task:lock:{userId}

    // ===== MinIO Bucket 名 =====
    public static final String BUCKET_SOURCE_TEMP = "images-source-temp";
    public static final String BUCKET_RESULT = "images-result";
    public static final String BUCKET_STYLE = "styles-cover";

    // ===== 验证码 =====
    public static final int CODE_LENGTH = 6;
    public static final int CODE_EXPIRE_MINUTES = 10;

    // ===== 图片上传 =====
    public static final int MAX_IMAGES_PER_TASK = 9;
    public static final int MIN_IMAGES_PER_TASK = 1;
}
