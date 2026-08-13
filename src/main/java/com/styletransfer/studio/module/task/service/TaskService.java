package com.styletransfer.studio.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.styletransfer.studio.common.constant.Constants;
import com.styletransfer.studio.common.enums.TaskItemStatus;
import com.styletransfer.studio.common.enums.TaskStatus;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.infra.mq.TaskMessageProducer;
import com.styletransfer.studio.module.quota.service.QuotaService;
import com.styletransfer.studio.module.style.entity.Style;
import com.styletransfer.studio.module.style.mapper.StyleMapper;
import com.styletransfer.studio.module.task.dto.CreateTaskDTO;
import com.styletransfer.studio.module.task.entity.Task;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import com.styletransfer.studio.module.task.mapper.TaskMapper;
import com.styletransfer.studio.module.task.vo.TaskDetailVO;
import com.styletransfer.studio.module.task.vo.TaskItemVO;
import com.styletransfer.studio.module.task.vo.TaskVO;
import com.styletransfer.studio.security.LoginUser;
import com.styletransfer.studio.security.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 任务服务：创建 / 详情 / 当前任务 / 取消
 *
 * <p>核心约束：
 * <ul>
 *   <li>单用户并发控制：Redisson 锁 user:task:lock:{userId}，tryLock(5,30,SECONDS)。</li>
 *   <li>按张额度扣减：调用 {@link QuotaService#deduct} 在锁内执行。</li>
 *   <li>创建流程：校验进行中 → 建 task(PENDING) → 建 N 条 task_item → 扣额度 → 投 MQ。</li>
 *   <li>若扣额度失败抛 QUOTA_NOT_ENOUGH，DB 由 @Transactional 回滚，Redis 在 deduct 内已回滚。</li>
 *   <li>若投 MQ 失败，手动 refund Redis 额度后抛异常，DB 由 @Transactional 回滚。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    /** 任务编号格式：T + yyyyMMddHHmmss + 4 位随机大写字母/数字 */
    private static final DateTimeFormatter TASK_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String CANCEL_REQUESTED_MARK = "CANCEL_REQUESTED";

    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final StyleMapper styleMapper;
    private final QuotaService quotaService;
    private final TaskMessageProducer taskMessageProducer;
    private final RedissonClient redissonClient;

    @Value("${minio.buckets.source-temp:images-source-temp}")
    private String sourceTempBucket;

    /**
     * 创建任务
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskVO createTask(CreateTaskDTO dto) {
        LoginUser ctx = UserContextHolder.getRequired();
        Long userId = ctx.userId();
        int n = dto.getFileKeys().size();

        // 二次校验图片数量（DTO 注解已保证）
        if (n < Constants.MIN_IMAGES_PER_TASK || n > Constants.MAX_IMAGES_PER_TASK) {
            throw new BizException(ResultCode.IMAGE_COUNT_INVALID);
        }

        // 校验风格
        Style style = styleMapper.selectById(dto.getStyleId());
        if (style == null) {
            throw new BizException(ResultCode.STYLE_NOT_FOUND);
        }
        if (style.getStatus() == null || style.getStatus() != 1) {
            throw new BizException(ResultCode.STYLE_OFFLINE);
        }

        // 获取 Redisson 锁
        RLock lock = redissonClient.getLock(String.format(Constants.REDIS_KEY_LOCK_USER_TASK, userId));
        boolean locked;
        try {
            locked = lock.tryLock(5, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.TASK_LOCK_FAIL);
        }
        if (!locked) {
            throw new BizException(ResultCode.TASK_LOCK_FAIL);
        }

        try {
            // 进行中任务校验（单用户并发=1）
            Long inProgress = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                    .eq(Task::getUserId, userId)
                    .in(Task::getStatus, TaskStatus.PENDING.name(), TaskStatus.PROCESSING.name()));
            if (inProgress != null && inProgress > 0) {
                throw new BizException(ResultCode.TASK_IN_PROGRESS);
            }

            // 建 task（PENDING）
            Task task = new Task();
            task.setTaskNo(generateTaskNo());
            task.setUserId(userId);
            task.setStyleId(dto.getStyleId());
            task.setImageCount(n);
            task.setCustomPrompt(dto.getCustomPrompt());
            task.setStatus(TaskStatus.PENDING.name());
            task.setSuccessCount(0);
            task.setFailCount(0);
            task.setOriginalCleaned(0);
            taskMapper.insert(task);

            // 建 N 条 task_item
            for (int i = 0; i < n; i++) {
                TaskItem item = new TaskItem();
                item.setTaskId(task.getId());
                item.setUserId(userId);
                item.setSeq(i + 1);
                item.setSourceFileKey(dto.getFileKeys().get(i));
                item.setSourceBucket(sourceTempBucket);
                item.setStatus(TaskItemStatus.PENDING.name());
                item.setRetryCount(0);
                taskItemMapper.insert(item);
            }

            // 扣额度（Redis DECRBY + DB user.quota + 流水）；失败抛 QUOTA_NOT_ENOUGH，DB 回滚
            quotaService.deduct(userId, n, task.getId());

            // 投 MQ：延迟到事务提交后执行，避免同进程消费者在事务提交前查不到 task 行
            final Long taskId = task.getId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            taskMessageProducer.sendCreate(taskId);
                        } catch (Exception ex) {
                            log.error("[TaskService] 事务提交后投递 MQ 失败 taskId={}，任务将卡在 PENDING", taskId, ex);
                        }
                    }
                });
            } else {
                // 无事务上下文（理论上不会走到），直接投递
                taskMessageProducer.sendCreate(taskId);
            }

            log.info("[TaskService] 任务创建成功 taskId={} taskNo={} userId={} imageCount={}",
                    task.getId(), task.getTaskNo(), userId, n);
            return toTaskVO(task);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 任务详情：校验归属当前用户；items 不含 sourceFileKey/resultFileKey。
     */
    public TaskDetailVO getTaskDetail(Long taskId) {
        Long userId = UserContextHolder.getRequired().userId();
        Task task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }

        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .orderByAsc(TaskItem::getSeq));

        return TaskDetailVO.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .status(task.getStatus())
                .styleId(task.getStyleId())
                .imageCount(task.getImageCount())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .customPrompt(task.getCustomPrompt())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .items(items.stream().map(this::toItemVO).toList())
                .build();
    }

    /**
     * 当前用户最近一条进行中任务，无则返回 null。
     */
    public TaskVO getCurrentTask() {
        Long userId = UserContextHolder.getRequired().userId();
        Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .in(Task::getStatus, TaskStatus.PENDING.name(), TaskStatus.PROCESSING.name())
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 1"));
        return task == null ? null : toTaskVO(task);
    }

    /**
     * 取消任务：
     * <ul>
     *   <li>PENDING → 直接置 CANCELED（终态）。原图清理由 Task 8 终态触发兜底。</li>
     *   <li>PROCESSING → errorMsg 写入 CANCEL_REQUESTED 标记，Worker 在处理下一 item 前检查。</li>
     *   <li>终态（SUCCESS/FAILED/CANCELED） → TASK_STATUS_INVALID。</li>
     * </ul>
     * 用户取消不回补额度（PRD）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskVO cancelTask(Long taskId) {
        Long userId = UserContextHolder.getRequired().userId();
        Task task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }

        TaskStatus status = TaskStatus.valueOf(task.getStatus());
        switch (status) {
            case PENDING -> taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getStatus, TaskStatus.CANCELED.name())
                    .set(Task::getFinishedAt, LocalDateTime.now()));
            case PROCESSING -> taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getErrorMsg, CANCEL_REQUESTED_MARK));
            default -> throw new BizException(ResultCode.TASK_STATUS_INVALID);
        }

        // 重新查询返回最新状态
        Task updated = taskMapper.selectById(taskId);
        log.info("[TaskService] 任务取消标记完成 taskId={} prevStatus={}", taskId, status);
        return toTaskVO(updated);
    }

    // ===== 私有方法 =====

    private TaskVO toTaskVO(Task task) {
        return TaskVO.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .status(task.getStatus())
                .imageCount(task.getImageCount())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }

    private TaskItemVO toItemVO(TaskItem item) {
        return TaskItemVO.builder()
                .id(item.getId())
                .seq(item.getSeq())
                .status(item.getStatus())
                .errorCode(item.getErrorCode())
                .errorMsg(item.getErrorMsg())
                .startedAt(item.getStartedAt())
                .finishedAt(item.getFinishedAt())
                .build();
    }

    /**
     * 生成任务编号：T + yyyyMMddHHmmss + 4 位随机大写字母/数字，如 T20260811143000A1B2
     */
    private String generateTaskNo() {
        String ts = LocalDateTime.now().format(TASK_NO_FMT);
        String rand = randomAlnum(4);
        return "T" + ts + rand;
    }

    private static final char[] ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private String randomAlnum(int len) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        char[] buf = new char[len];
        for (int i = 0; i < len; i++) {
            buf[i] = ALNUM[r.nextInt(ALNUM.length)];
        }
        return new String(buf);
    }
}
