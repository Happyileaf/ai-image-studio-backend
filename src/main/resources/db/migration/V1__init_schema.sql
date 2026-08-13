-- =====================================================================
-- V1: 初始化数据库 schema（MVP 核心表）
-- 对应《技术方案文档》3.2 节表结构
-- Flyway 在应用启动时自动执行；重复运行幂等安全。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. user 用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户 ID',
    `email`         VARCHAR(128)    NOT NULL                 COMMENT '邮箱（登录凭证）',
    `password_hash` VARCHAR(100)    NOT NULL                 COMMENT 'BCrypt 加密密码',
    `nickname`      VARCHAR(64)     NULL                     COMMENT '昵称（可选）',
    `role`          VARCHAR(16)     NOT NULL DEFAULT 'USER'  COMMENT '角色：USER / ADMIN',
    `quota`         INT             NOT NULL DEFAULT 0       COMMENT '当前剩余额度（按张计）',
    `status`        TINYINT         NOT NULL DEFAULT 1       COMMENT '0 禁用 1 正常',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除 0 未删除 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------------------------------------------------------------------
-- 2. email_code 邮箱验证码表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `email_code` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `email`         VARCHAR(128)    NOT NULL                 COMMENT '邮箱',
    `code`          VARCHAR(6)      NOT NULL                 COMMENT '6 位验证码',
    `purpose`       VARCHAR(16)     NOT NULL                 COMMENT '用途：REGISTER / RESET_PASSWORD',
    `expire_at`     DATETIME        NOT NULL                 COMMENT '过期时间（10 分钟）',
    `used`          TINYINT         NOT NULL DEFAULT 0       COMMENT '是否已使用 0 否 1 是',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_email_purpose_used` (`email`, `purpose`, `used`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证码表';

-- ---------------------------------------------------------------------
-- 3. style 风格表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `style` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '风格 ID',
    `name`              VARCHAR(64)     NOT NULL                 COMMENT '风格名',
    `category`          VARCHAR(32)     NOT NULL                 COMMENT '分类：PAINTING / PHOTO / ART',
    `cover_key`         VARCHAR(255)    NULL                     COMMENT '封面图 key',
    `prompt_template`   TEXT            NOT NULL                 COMMENT '提示词模板',
    `negative_prompt`   TEXT            NULL                     COMMENT '负向提示词',
    `sort_order`        INT             NOT NULL DEFAULT 0       COMMENT '排序（越小越前）',
    `status`            TINYINT         NOT NULL DEFAULT 0       COMMENT '0 下架 1 上架',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_style_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风格表';

-- ---------------------------------------------------------------------
-- 4. task 任务表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '任务 ID',
    `task_no`           VARCHAR(32)     NOT NULL                 COMMENT '业务编号（用户可读）',
    `user_id`           BIGINT          NOT NULL                 COMMENT '所属用户',
    `style_id`          BIGINT          NOT NULL                 COMMENT '风格 ID',
    `image_count`       INT             NOT NULL                 COMMENT '图片张数（1~9）',
    `custom_prompt`     VARCHAR(500)    NULL                     COMMENT '用户自定义提示词',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/CANCELED',
    `success_count`     INT             NOT NULL DEFAULT 0       COMMENT '成功张数',
    `fail_count`        INT             NOT NULL DEFAULT 0       COMMENT '失败张数',
    `error_msg`         VARCHAR(500)    NULL                     COMMENT '失败原因汇总',
    `started_at`        DATETIME        NULL                     COMMENT '开始处理时间',
    `finished_at`       DATETIME        NULL                     COMMENT '完成时间',
    `original_cleaned`  TINYINT         NOT NULL DEFAULT 0       COMMENT '原图是否已清理 0 否 1 是',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_task_no` (`task_no`),
    KEY `idx_task_user_status` (`user_id`, `status`),
    KEY `idx_task_status` (`status`),
    KEY `idx_task_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风格迁移任务表';

-- ---------------------------------------------------------------------
-- 5. task_item 任务项表（每张原图一项）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_item` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `task_id`           BIGINT          NOT NULL                 COMMENT '任务 ID',
    `user_id`           BIGINT          NOT NULL                 COMMENT '用户 ID（冗余便于清理）',
    `seq`               INT             NOT NULL                 COMMENT '任务内序号（1~9）',
    `source_file_key`   VARCHAR(255)    NULL                     COMMENT '原图临时 key（清理后置空，隐私红线）',
    `source_bucket`     VARCHAR(64)     NULL                     COMMENT '原图 bucket',
    `result_file_key`   VARCHAR(255)    NULL                     COMMENT '结果图 key（成功后写入）',
    `result_bucket`     VARCHAR(64)     NULL                     COMMENT '结果图 bucket',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED/CANCELED',
    `error_code`        VARCHAR(64)     NULL                     COMMENT '失败错误码',
    `error_msg`         VARCHAR(500)    NULL                     COMMENT '失败原因',
    `retry_count`       INT             NOT NULL DEFAULT 0       COMMENT '重试次数',
    `started_at`        DATETIME        NULL,
    `finished_at`       DATETIME        NULL,
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_item_task` (`task_id`),
    KEY `idx_task_item_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务项表（每张原图一项）';

-- ---------------------------------------------------------------------
-- 6. generated_image 用户生成图片表（历史维度 / 用户作品集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `generated_image` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL                 COMMENT '用户 ID',
    `task_id`       BIGINT          NOT NULL                 COMMENT '来源任务',
    `task_item_id`  BIGINT          NOT NULL                 COMMENT '来源任务项',
    `style_id`      BIGINT          NOT NULL                 COMMENT '风格 ID',
    `style_name`    VARCHAR(64)     NOT NULL                 COMMENT '风格名快照（冗余，列表展示用）',
    `file_key`      VARCHAR(255)    NOT NULL                 COMMENT '结果图 key',
    `bucket`        VARCHAR(64)     NOT NULL                 COMMENT '结果图 bucket',
    `width`         INT             NULL                     COMMENT '宽（像素）',
    `height`        INT             NULL                     COMMENT '高（像素）',
    `size`          BIGINT          NULL                     COMMENT '字节大小',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间（排序字段）',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除（同步删文件）',
    PRIMARY KEY (`id`),
    KEY `idx_gen_image_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户生成图片表（历史维度）';

-- ---------------------------------------------------------------------
-- 7. quota_record 额度变更记录表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quota_record` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL                 COMMENT '用户 ID',
    `delta`         INT             NOT NULL                 COMMENT '变化量（正为增加，负为扣减）',
    `balance`       INT             NOT NULL                 COMMENT '变更后余额（快照）',
    `reason`        VARCHAR(32)     NOT NULL                 COMMENT 'TASK_CREATE / TASK_FAIL_REFUND / ADMIN_ADJUST',
    `task_id`       BIGINT          NULL                     COMMENT '关联任务（可空）',
    `task_item_id`  BIGINT          NULL                     COMMENT '关联任务项（按张回补时）',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_quota_record_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='额度变更记录表';

-- ---------------------------------------------------------------------
-- 8. sys_config 系统配置表（后台管理系统配置分组表单）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `config_key`    VARCHAR(128)    NOT NULL                 COMMENT '配置键，例如 ai.provider / quota.default',
    `config_value`  TEXT            NOT NULL                 COMMENT '配置值',
    `description`   VARCHAR(255)    NULL                     COMMENT '说明',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- =====================================================================
-- 初始化数据：预置 5~8 个风格（MVP 基础版，封面图后续通过 Admin 上传）
-- =====================================================================
INSERT INTO `style` (`name`, `category`, `prompt_template`, `sort_order`, `status`) VALUES
('油画',       'PAINTING', 'oil painting style, masterpiece, highly detailed, rich texture, classical composition, {{content}}{{user_prompt}}', 1, 1),
('水彩',       'PAINTING', 'watercolor painting style, soft washes, translucent colors, artistic paper texture, {{content}}{{user_prompt}}', 2, 1),
('素描',       'PAINTING', 'pencil sketch style, hand drawn, cross hatching, graphite shading, monochrome, {{content}}{{user_prompt}}', 3, 1),
('胶片',       'PHOTO',    'film photography, 35mm, grain, warm tones, vintage color grading, cinematic, {{content}}{{user_prompt}}', 4, 1),
('赛博朋克',    'ART',      'cyberpunk style, neon lights, futuristic city, high contrast, rain, holographic, {{content}}{{user_prompt}}', 5, 1),
('吉卜力动画',  'ART',      'studio ghibli style, anime, hand drawn, soft colors, whimsical, warm lighting, {{content}}{{user_prompt}}', 6, 1);

-- 初始化系统配置默认值（与 application.yml 对齐，后台可修改）
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ai.provider',              'stub',        'AI 模型供应商（stub 桩实现 / stability / replicate / openrouter）'),
('quota.default',            '20',          '新用户默认额度（张）'),
('task.user-concurrent',     '1',           '单用户同时进行中任务上限'),
('task.max-images',          '9',           '单任务最大图片数'),
('task.min-images',          '1',           '单任务最小图片数'),
('upload.max-size-mb',       '10',          '单图最大大小(MB)'),
('upload.max-resolution',    '4096x4096',   '最大分辨率(宽x高)'),
('upload.allowed-types',     'jpg,png,webp','支持格式'),
('cleanup.original-retention-minutes', '120', '原图临时保存上限(分钟)（超过强制清理兜底）'),
('cleanup.result-retention-days',     '7',   '历史结果图保留天数（到期自动删除文件与记录，≥1）'),
('cleanup.result-cron',      '0 30 3 * * *','结果图过期清理计划 cron（每日 03:30）'),
('cleanup.orphan-cron',      '0 0 3 * * *', '孤儿图片扫描计划 cron（每日 03:00）'),
('email.verify-code-template',  'verify-code-template-01', '验证码邮件模板 ID'),
('email.code-expire-minutes',   '10',          '验证码有效期(分钟)'),
('email.send-rate-limit',       '1/60',        '同邮箱验证码发送频率（次/秒）'),
('email.send-daily-limit',      '20',          '同邮箱单日上限(次)');
