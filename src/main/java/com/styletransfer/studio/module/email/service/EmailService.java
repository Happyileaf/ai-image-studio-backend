package com.styletransfer.studio.module.email.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.styletransfer.studio.common.constant.Constants;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.module.email.entity.EmailCode;
import com.styletransfer.studio.module.email.infra.mail.ResendProvider;
import com.styletransfer.studio.module.email.mapper.EmailCodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 *
 * <p>验证码以 Redis 为准（10 分钟 TTL），email_code 表仅作审计。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 60s 内同邮箱限频 */
    private static final long RATE_LIMIT_SECONDS = 60L;
    /** 单日同邮箱发送上限 */
    private static final int DAILY_LIMIT = 20;
    /** 验证码有效期（秒） */
    private static final long CODE_EXPIRE_SECONDS = Constants.CODE_EXPIRE_MINUTES * 60L;

    private final StringRedisTemplate stringRedisTemplate;
    private final EmailCodeMapper emailCodeMapper;
    private final ResendProvider resendProvider;

    /**
     * 生成并发送验证码
     *
     * @return 验证码有效期（秒）
     */
    public long generateAndSend(String email, String purpose) {
        // 1. 60s 限频
        String rateKey = String.format(Constants.REDIS_KEY_EMAIL_CODE_RATE, email);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(rateKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new BizException(ResultCode.RATE_LIMITED, "验证码发送过于频繁，请 60 秒后重试");
        }

        // 2. 单日上限
        String dailyKey = String.format(Constants.REDIS_KEY_EMAIL_CODE_DAILY, email, LocalDate.now().format(DATE_FMT));
        Long dailyCount = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            stringRedisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
        }
        if (dailyCount != null && dailyCount > DAILY_LIMIT) {
            throw new BizException(ResultCode.RATE_LIMITED, "今日验证码发送次数已达上限");
        }

        // 3. 生成 6 位数字验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));

        // 4. 写入 Redis（10 分钟有效）
        String codeKey = String.format(Constants.REDIS_KEY_EMAIL_CODE, email, purpose);
        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 5. 写入 email_code 表（审计）
        EmailCode emailCode = new EmailCode();
        emailCode.setEmail(email);
        emailCode.setCode(code);
        emailCode.setPurpose(purpose);
        emailCode.setExpireAt(LocalDateTime.now().plusMinutes(Constants.CODE_EXPIRE_MINUTES));
        emailCode.setUsed(0);
        emailCodeMapper.insert(emailCode);

        // 6. 调用 Resend 发送
        resendProvider.sendVerifyCode(email, code, purpose);

        log.info("[EmailService] 验证码已生成 email={} purpose={}", email, purpose);
        return CODE_EXPIRE_SECONDS;
    }

    /**
     * 校验验证码：成功后标记 used=1 并删除 Redis key
     */
    public void verify(String email, String purpose, String code) {
        String codeKey = String.format(Constants.REDIS_KEY_EMAIL_CODE, email, purpose);
        String stored = stringRedisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BizException(ResultCode.CODE_EXPIRED);
        }
        if (!stored.equals(code)) {
            throw new BizException(ResultCode.CODE_INVALID);
        }
        // 匹配：标记最近一条审计记录 used=1，删除 Redis
        markUsed(email, purpose, code);
        stringRedisTemplate.delete(codeKey);
    }

    private void markUsed(String email, String purpose, String code) {
        try {
            EmailCode latest = emailCodeMapper.selectOne(new LambdaQueryWrapper<EmailCode>()
                    .eq(EmailCode::getEmail, email)
                    .eq(EmailCode::getPurpose, purpose)
                    .eq(EmailCode::getCode, code)
                    .eq(EmailCode::getUsed, 0)
                    .orderByDesc(EmailCode::getId)
                    .last("LIMIT 1"));
            if (latest != null) {
                emailCodeMapper.update(null, new LambdaUpdateWrapper<EmailCode>()
                        .eq(EmailCode::getId, latest.getId())
                        .set(EmailCode::getUsed, 1));
            }
        } catch (Exception e) {
            log.warn("[EmailService] 标记验证码 used 失败 email={} purpose={}", email, purpose, e);
        }
    }
}
