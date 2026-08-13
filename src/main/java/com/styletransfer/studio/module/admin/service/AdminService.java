package com.styletransfer.studio.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.styletransfer.studio.common.enums.TaskItemStatus;
import com.styletransfer.studio.common.enums.TaskStatus;
import com.styletransfer.studio.common.exception.BizException;
import com.styletransfer.studio.common.result.ResultCode;
import com.styletransfer.studio.infra.mq.TaskMessageProducer;
import com.styletransfer.studio.infra.storage.MinioStorageService;
import com.styletransfer.studio.module.admin.dto.AdminStyleEditDTO;
import com.styletransfer.studio.module.admin.dto.QuotaAdjustDTO;
import com.styletransfer.studio.module.admin.dto.SysConfigBatchDTO;
import com.styletransfer.studio.module.admin.entity.SysConfig;
import com.styletransfer.studio.module.admin.mapper.SysConfigMapper;
import com.styletransfer.studio.module.admin.vo.AdminStyleVO;
import com.styletransfer.studio.module.admin.vo.AdminTaskDetailVO;
import com.styletransfer.studio.module.admin.vo.AdminTaskItemVO;
import com.styletransfer.studio.module.admin.vo.AdminTaskVO;
import com.styletransfer.studio.module.admin.vo.AdminUserVO;
import com.styletransfer.studio.module.admin.vo.DashboardVO;
import com.styletransfer.studio.module.admin.vo.QuotaRecordVO;
import com.styletransfer.studio.module.admin.vo.SysConfigVO;
import com.styletransfer.studio.module.quota.entity.QuotaRecord;
import com.styletransfer.studio.module.quota.mapper.QuotaRecordMapper;
import com.styletransfer.studio.module.quota.service.QuotaService;
import com.styletransfer.studio.module.style.entity.Style;
import com.styletransfer.studio.module.style.mapper.StyleMapper;
import com.styletransfer.studio.module.task.entity.Task;
import com.styletransfer.studio.module.task.entity.TaskItem;
import com.styletransfer.studio.module.task.mapper.TaskItemMapper;
import com.styletransfer.studio.module.task.mapper.TaskMapper;
import com.styletransfer.studio.module.user.entity.User;
import com.styletransfer.studio.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 后台管理服务：仪表盘 / 用户 / 风格 / 任务 / 系统配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    /** CONTENT_VIOLATION 错误码：违规不可重试 */
    private static final String ERR_CONTENT_VIOLATION = "CONTENT_VIOLATION";

    private final UserMapper userMapper;
    private final StyleMapper styleMapper;
    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final QuotaService quotaService;
    private final SysConfigMapper sysConfigMapper;
    private final MinioStorageService minioStorageService;
    private final TaskMessageProducer taskMessageProducer;

    @Value("${minio.buckets.style:styles-cover}")
    private String styleBucket;

    // ===================== 1. 仪表盘 =====================

    /**
     * 仪表盘统计：今日新增用户 / 今日任务数 / 今日成功率 / 进行中任务数
     */
    public DashboardVO dashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        Long todayNewUsers = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, todayStart));

        Long todayTaskTotal = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .ge(Task::getCreatedAt, todayStart));

        Long todaySuccess = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .ge(Task::getCreatedAt, todayStart)
                .eq(Task::getStatus, TaskStatus.SUCCESS.name()));
        Long todayFailed = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .ge(Task::getCreatedAt, todayStart)
                .eq(Task::getStatus, TaskStatus.FAILED.name()));
        double successRate;
        long denom = todaySuccess + todayFailed;
        if (denom <= 0) {
            successRate = 0.0;
        } else {
            successRate = todaySuccess * 100.0 / denom;
        }

        Long inProgressTasks = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus, TaskStatus.PENDING.name(), TaskStatus.PROCESSING.name()));

        return DashboardVO.builder()
                .todayNewUsers(todayNewUsers)
                .todayTaskTotal(todayTaskTotal)
                .todaySuccessRate(successRate)
                .inProgressTasks(inProgressTasks)
                .build();
    }

    // ===================== 2. 用户管理 =====================

    /**
     * 用户分页查询：支持 email 模糊 / status / 注册时间范围
     */
    public IPage<AdminUserVO> pageUsers(int page, int size,
                                        String email, Integer status,
                                        LocalDateTime startDate, LocalDateTime endDate) {
        Page<User> p = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(email), User::getEmail, email)
                .eq(status != null, User::getStatus, status)
                .ge(startDate != null, User::getCreatedAt, startDate)
                .le(endDate != null, User::getCreatedAt, endDate)
                .orderByDesc(User::getCreatedAt);
        IPage<User> result = userMapper.selectPage(p, wrapper);
        return result.convert(this::toAdminUserVO);
    }

    /**
     * 调整用户额度：delta 正为增加（refund），负为扣减（DECRBY）。
     * 复用 QuotaService：refund 支持正数；负数走 deduct 语义（count 正数）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int adjustQuota(Long userId, QuotaAdjustDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.EMAIL_NOT_FOUND);
        }
        int delta = dto.getDelta();
        String reason = StringUtils.hasText(dto.getReason()) ? dto.getReason() : QuotaService.REASON_ADMIN_ADJUST;

        int balance;
        if (delta >= 0) {
            // 增加额度：refund 语义
            balance = quotaService.refund(userId, delta, null, null,
                    QuotaService.REASON_ADMIN_ADJUST);
        } else {
            // 扣减额度：Redis DECRBY + DB user.quota + 流水（reason=ADMIN_ADJUST）
            // QuotaService.deduct 内部固定 reason=TASK_CREATE，不适用 admin 场景，需独立实现
            balance = deductForAdmin(userId, -delta, reason);
        }
        log.info("[AdminService] 调整额度 userId={} delta={} balance={} reason={}",
                userId, delta, balance, reason);
        return balance;
    }

    /**
     * 管理员扣减额度：Redis DECRBY + DB user.quota + 流水 reason=ADMIN_ADJUST。
     * 允许扣到负数（管理员强制调整，不走 QUOTA_NOT_ENOUGH 校验）。
     */
    private int deductForAdmin(Long userId, int count, String reason) {
        // 直接基于 DB user.quota 增量更新（不依赖 Redis 缓存当前值，避免懒加载竞争）
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("quota = quota - " + count));
        User updated = userMapper.selectById(userId);
        int balance = updated != null && updated.getQuota() != null ? updated.getQuota() : 0;

        // 同步 Redis 缓存（覆盖写）
        quotaService.initUserQuota(userId, balance);

        // 写流水
        QuotaRecord record = new QuotaRecord();
        record.setUserId(userId);
        record.setDelta(-count);
        record.setBalance(balance);
        record.setReason(reason);
        quotaRecordMapper.insert(record);
        return balance;
    }

    /**
     * 更新用户状态（0 禁用 1 启用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.EMAIL_NOT_FOUND);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, status));
        log.info("[AdminService] 更新用户状态 userId={} status={}", userId, status);
    }

    // ===================== 3. 风格管理 =====================

    /**
     * 创建风格（封面图可选）
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminStyleVO createStyle(AdminStyleEditDTO dto, MultipartFile coverFile) {
        Style style = new Style();
        style.setName(dto.getName());
        style.setCategory(dto.getCategory());
        style.setPromptTemplate(dto.getPromptTemplate());
        style.setNegativePrompt(dto.getNegativePrompt());
        style.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        style.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        if (coverFile != null && !coverFile.isEmpty()) {
            style.setCoverKey(uploadCover(coverFile));
        }
        styleMapper.insert(style);
        log.info("[AdminService] 创建风格 id={} name={}", style.getId(), style.getName());
        return toAdminStyleVO(style);
    }

    /**
     * 编辑风格（封面图可选更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminStyleVO updateStyle(Long id, AdminStyleEditDTO dto, MultipartFile coverFile) {
        Style style = styleMapper.selectById(id);
        if (style == null) {
            throw new BizException(ResultCode.STYLE_NOT_FOUND);
        }
        style.setName(dto.getName());
        style.setCategory(dto.getCategory());
        style.setPromptTemplate(dto.getPromptTemplate());
        style.setNegativePrompt(dto.getNegativePrompt());
        if (dto.getSortOrder() != null) {
            style.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            style.setStatus(dto.getStatus());
        }
        if (coverFile != null && !coverFile.isEmpty()) {
            style.setCoverKey(uploadCover(coverFile));
        }
        styleMapper.updateById(style);
        log.info("[AdminService] 编辑风格 id={} name={}", id, style.getName());
        return toAdminStyleVO(style);
    }

    /**
     * 风格上下架
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStyleStatus(Long id, Integer status) {
        Style style = styleMapper.selectById(id);
        if (style == null) {
            throw new BizException(ResultCode.STYLE_NOT_FOUND);
        }
        styleMapper.update(null, new LambdaUpdateWrapper<Style>()
                .eq(Style::getId, id)
                .set(Style::getStatus, status));
        log.info("[AdminService] 风格上下架 id={} status={}", id, status);
    }

    /**
     * 上传封面图到 styles-cover 桶，objectKey = cover/{uuid}.{ext}
     */
    private String uploadCover(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = "jpg";
        if (StringUtils.hasText(original) && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        String objectKey = "cover/" + UUID.randomUUID() + "." + ext;
        try {
            minioStorageService.uploadObject(styleBucket, objectKey,
                    file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "封面图读取失败");
        }
        return objectKey;
    }

    // ===================== 4. 任务监控 =====================

    /**
     * 全站任务分页查询：支持 taskId / email / status / styleId / 完成时间范围
     */
    public IPage<AdminTaskVO> pageTasks(int page, int size,
                                        Long taskId, String email, String status, Long styleId,
                                        LocalDateTime startDate, LocalDateTime endDate) {
        // 若按 email 过滤，先查 user 命中列表
        List<Long> matchedUserIds = null;
        if (StringUtils.hasText(email)) {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .like(User::getEmail, email));
            matchedUserIds = users.stream().map(User::getId).toList();
            if (matchedUserIds.isEmpty()) {
                // 无匹配用户，直接返回空页
                return new Page<>(page, size, 0);
            }
        }

        Page<Task> p = new Page<>(page, size);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(taskId != null, Task::getId, taskId)
                .eq(StringUtils.hasText(status), Task::getStatus, status)
                .eq(styleId != null, Task::getStyleId, styleId)
                .in(matchedUserIds != null, Task::getUserId, matchedUserIds)
                .ge(startDate != null, Task::getFinishedAt, startDate)
                .le(endDate != null, Task::getFinishedAt, endDate)
                .orderByDesc(Task::getCreatedAt);
        IPage<Task> result = taskMapper.selectPage(p, wrapper);

        // 批量加载 user/style 名称映射
        Map<Long, String> userEmailMap = new HashMap<>();
        Map<Long, String> styleNameMap = new HashMap<>();
        for (Task t : result.getRecords()) {
            userEmailMap.computeIfAbsent(t.getUserId(), this::loadUserEmail);
            styleNameMap.computeIfAbsent(t.getStyleId(), this::loadStyleName);
        }

        return result.convert(t -> toAdminTaskVO(t, userEmailMap.get(t.getUserId()), styleNameMap.get(t.getStyleId())));
    }

    /**
     * 任务详情 + 子项明细（不含 fileKey）+ 额度流水
     */
    public AdminTaskDetailVO getTaskDetail(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }

        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .orderByAsc(TaskItem::getSeq));

        List<QuotaRecord> records = quotaRecordMapper.selectList(new LambdaQueryWrapper<QuotaRecord>()
                .eq(QuotaRecord::getTaskId, taskId)
                .orderByAsc(QuotaRecord::getCreatedAt));

        return AdminTaskDetailVO.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .userId(task.getUserId())
                .userEmail(loadUserEmail(task.getUserId()))
                .styleId(task.getStyleId())
                .styleName(loadStyleName(task.getStyleId()))
                .imageCount(task.getImageCount())
                .customPrompt(task.getCustomPrompt())
                .status(task.getStatus())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .errorMsg(task.getErrorMsg())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .items(items.stream().map(this::toAdminTaskItemVO).toList())
                .quotaRecords(records.stream().map(this::toQuotaRecordVO).toList())
                .build();
    }

    /**
     * 重试任务：仅 FAILED 状态、且存在非 CONTENT_VIOLATION 的失败 item 可重试。
     * 重置失败 item 状态为 PENDING、retryCount=0，task.status 置 PENDING，重新投递 MQ。
     */
    @Transactional(rollbackFor = Exception.class)
    public void retryTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        if (!TaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID);
        }

        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .orderByAsc(TaskItem::getSeq));

        boolean hasRetryable = false;
        for (TaskItem item : items) {
            if (TaskItemStatus.FAILED.name().equals(item.getStatus())
                    && !ERR_CONTENT_VIOLATION.equals(item.getErrorCode())) {
                taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                        .eq(TaskItem::getId, item.getId())
                        .set(TaskItem::getStatus, TaskItemStatus.PENDING.name())
                        .set(TaskItem::getRetryCount, 0)
                        .set(TaskItem::getErrorCode, null)
                        .set(TaskItem::getErrorMsg, null)
                        .set(TaskItem::getStartedAt, null)
                        .set(TaskItem::getFinishedAt, null));
                hasRetryable = true;
            }
        }
        if (!hasRetryable) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID);
        }

        // task 置 PENDING，清空 startedAt/finishedAt/errorMsg，等待 Worker 重新拉取
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .set(Task::getStatus, TaskStatus.PENDING.name())
                .set(Task::getStartedAt, null)
                .set(Task::getFinishedAt, null)
                .set(Task::getErrorMsg, null));

        // 重新投递 MQ 消息
        try {
            taskMessageProducer.sendCreate(taskId);
        } catch (Exception e) {
            log.error("[AdminService] 重试任务投递 MQ 失败 taskId={}", taskId, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "任务重试投递失败，请稍后重试");
        }
        log.info("[AdminService] 任务重试 taskId={}", taskId);
    }

    // ===================== 5. 系统配置 =====================

    /**
     * 返回所有 sys_config
     */
    public List<SysConfigVO> listConfigs() {
        List<SysConfig> list = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getId));
        return list.stream().map(this::toSysConfigVO).toList();
    }

    /**
     * 批量更新 sys_config（写后即生效；部分配置需重启或定时刷新）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SysConfigVO> updateConfigs(SysConfigBatchDTO dto) {
        for (SysConfigBatchDTO.Item item : dto.getConfigs()) {
            if (!StringUtils.hasText(item.getKey())) {
                continue;
            }
            SysConfig existing = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, item.getKey()));
            if (existing == null) {
                SysConfig nc = new SysConfig();
                nc.setConfigKey(item.getKey());
                nc.setConfigValue(item.getValue());
                sysConfigMapper.insert(nc);
            } else {
                sysConfigMapper.update(null, new LambdaUpdateWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, item.getKey())
                        .set(SysConfig::getConfigValue, item.getValue()));
            }
        }
        log.info("[AdminService] 批量更新系统配置 count={}", dto.getConfigs().size());
        return listConfigs();
    }

    // ===================== 私有映射方法 =====================

    private AdminUserVO toAdminUserVO(User u) {
        return AdminUserVO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nickname(u.getNickname())
                .quota(u.getQuota())
                .role(u.getRole())
                .status(u.getStatus())
                .createdAt(u.getCreatedAt())
                .build();
    }

    private AdminTaskVO toAdminTaskVO(Task t, String userEmail, String styleName) {
        Long duration = null;
        if (t.getStartedAt() != null && t.getFinishedAt() != null) {
            duration = java.time.Duration.between(t.getStartedAt(), t.getFinishedAt()).getSeconds();
        }
        return AdminTaskVO.builder()
                .id(t.getId())
                .taskNo(t.getTaskNo())
                .userId(t.getUserId())
                .userEmail(userEmail)
                .styleId(t.getStyleId())
                .styleName(styleName)
                .imageCount(t.getImageCount())
                .status(t.getStatus())
                .successCount(t.getSuccessCount())
                .failCount(t.getFailCount())
                .durationSeconds(duration)
                .createdAt(t.getCreatedAt())
                .build();
    }

    private AdminTaskItemVO toAdminTaskItemVO(TaskItem item) {
        // 严禁返回 sourceFileKey / resultFileKey
        return AdminTaskItemVO.builder()
                .id(item.getId())
                .seq(item.getSeq())
                .status(item.getStatus())
                .errorCode(item.getErrorCode())
                .errorMsg(item.getErrorMsg())
                .retryCount(item.getRetryCount())
                .build();
    }

    private QuotaRecordVO toQuotaRecordVO(QuotaRecord r) {
        return QuotaRecordVO.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .delta(r.getDelta())
                .balance(r.getBalance())
                .reason(r.getReason())
                .taskId(r.getTaskId())
                .taskItemId(r.getTaskItemId())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private SysConfigVO toSysConfigVO(SysConfig c) {
        return SysConfigVO.builder()
                .key(c.getConfigKey())
                .value(c.getConfigValue())
                .description(c.getDescription())
                .build();
    }

    private AdminStyleVO toAdminStyleVO(Style s) {
        String coverUrl = null;
        if (StringUtils.hasText(s.getCoverKey())) {
            coverUrl = minioStorageService.presignedGetUrl(styleBucket, s.getCoverKey(), 60);
        }
        return AdminStyleVO.builder()
                .id(s.getId())
                .name(s.getName())
                .category(s.getCategory())
                .coverUrl(coverUrl)
                .promptTemplate(s.getPromptTemplate())
                .negativePrompt(s.getNegativePrompt())
                .sortOrder(s.getSortOrder())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private String loadUserEmail(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        return u != null ? u.getEmail() : null;
    }

    private String loadStyleName(Long styleId) {
        if (styleId == null) {
            return null;
        }
        Style s = styleMapper.selectById(styleId);
        return s != null ? s.getName() : null;
    }
}
