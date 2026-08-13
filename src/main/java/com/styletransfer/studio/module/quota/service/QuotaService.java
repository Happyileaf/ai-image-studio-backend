package com.styletransfer.studio.module.quota.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.styletransfer.studio.common.constant.Constants;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.module.quota.entity.QuotaRecord;
import com.styletransfer.studio.module.quota.mapper.QuotaRecordMapper;
import com.styletransfer.studio.module.user.entity.User;
import com.styletransfer.studio.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 额度服务：按张额度扣减 / 回补 / 查询
 *
 * <p>设计：
 * <ul>
 *   <li>Redis 作为额度计数缓存（quota:{userId}），DECRBY/INCRBY 原子操作。</li>
 *   <li>DB user.quota 字段 + quota_record 流水做持久化。</li>
 *   <li>懒加载：deduct 时若 key 不存在，先从 DB 读 user.quota 写入 Redis 再 DECRBY。</li>
 *   <li>调用方（TaskService）持有 Redisson 锁 user:task:lock:{userId}，本服务方法不再加锁。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    /** 额度变更原因常量 */
    public static final String REASON_TASK_CREATE = "TASK_CREATE";
    public static final String REASON_TASK_FAIL_REFUND = "TASK_FAIL_REFUND";
    public static final String REASON_ADMIN_ADJUST = "ADMIN_ADJUST";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final QuotaRecordMapper quotaRecordMapper;

    /**
     * 扣减额度（调用方持有 Redisson 锁）。
     *
     * <p>流程：懒加载 Redis key → DECRBY count → 若结果 &lt; 0 回滚并抛 QUOTA_NOT_ENOUGH
     * → 更新 DB user.quota → 写 quota_record 流水。
     *
     * @param userId 用户 ID
     * @param count  扣减张数（正数）
     * @param taskId 关联任务 ID（可空）
     * @return 扣减后新余额
     */
    public int deduct(Long userId, int count, Long taskId) {
        String key = quotaKey(userId);

        // 懒加载：key 不存在时从 DB 读 user.quota 写入 Redis
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(hasKey)) {
            User user = userMapper.selectById(userId);
            int dbQuota = user != null && user.getQuota() != null ? user.getQuota() : 0;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbQuota));
            log.info("[QuotaService] 懒加载额度缓存 userId={} quota={}", userId, dbQuota);
        }

        // DECRBY count（incrementValue 传负数）
        Long newBalance = stringRedisTemplate.opsForValue().increment(key, -count);
        if (newBalance == null || newBalance < 0) {
            // 回滚 Redis
            stringRedisTemplate.opsForValue().increment(key, count);
            log.warn("[QuotaService] 额度不足 userId={} need={} current={}", userId, count, newBalance);
            throw new BizException(ResultCode.QUOTA_NOT_ENOUGH);
        }

        int balance = newBalance.intValue();

        // 更新 DB user.quota（增量更新，与 Redis 保持一致）
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("quota = quota + " + (-count)));

        // 写流水
        QuotaRecord record = new QuotaRecord();
        record.setUserId(userId);
        record.setDelta(-count);
        record.setBalance(balance);
        record.setReason(REASON_TASK_CREATE);
        record.setTaskId(taskId);
        quotaRecordMapper.insert(record);

        log.info("[QuotaService] 扣减额度 userId={} count={} balance={} taskId={}",
                userId, count, balance, taskId);
        return balance;
    }

    /**
     * 回补额度（任务失败按张回补 / 管理员调整）。
     *
     * @param userId     用户 ID
     * @param count      回补张数（正数）
     * @param taskId     关联任务 ID（可空）
     * @param taskItemId 关联任务项 ID（按张回补时）
     * @param reason     REASON_TASK_FAIL_REFUND / REASON_ADMIN_ADJUST
     * @return 回补后新余额
     */
    public int refund(Long userId, int count, Long taskId, Long taskItemId, String reason) {
        String key = quotaKey(userId);

        // 懒加载保证 key 存在
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(hasKey)) {
            User user = userMapper.selectById(userId);
            int dbQuota = user != null && user.getQuota() != null ? user.getQuota() : 0;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(dbQuota));
        }

        Long newBalance = stringRedisTemplate.opsForValue().increment(key, count);
        int balance = newBalance != null ? newBalance.intValue() : 0;

        // 更新 DB
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("quota = quota + " + count));

        // 写流水
        QuotaRecord record = new QuotaRecord();
        record.setUserId(userId);
        record.setDelta(count);
        record.setBalance(balance);
        record.setReason(reason);
        record.setTaskId(taskId);
        record.setTaskItemId(taskItemId);
        quotaRecordMapper.insert(record);

        log.info("[QuotaService] 回补额度 userId={} count={} balance={} reason={} taskId={} taskItemId={}",
                userId, count, balance, reason, taskId, taskItemId);
        return balance;
    }

    /**
     * 初始化用户额度缓存（注册时调用，Redis SET）。
     *
     * @param userId        用户 ID
     * @param defaultQuota  默认额度
     */
    public void initUserQuota(Long userId, int defaultQuota) {
        String key = quotaKey(userId);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(defaultQuota));
        log.info("[QuotaService] 初始化额度缓存 userId={} quota={}", userId, defaultQuota);
    }

    /**
     * 查询当前额度：优先 Redis，不存在则从 DB 加载并回填 Redis。
     */
    public int getQuota(Long userId) {
        String key = quotaKey(userId);
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignore) {
                // fall through to DB
            }
        }
        User user = userMapper.selectById(userId);
        int dbQuota = user != null && user.getQuota() != null ? user.getQuota() : 0;
        stringRedisTemplate.opsForValue().set(key, String.valueOf(dbQuota));
        return dbQuota;
    }

    private String quotaKey(Long userId) {
        return String.format(Constants.REDIS_KEY_QUOTA, userId);
    }
}
