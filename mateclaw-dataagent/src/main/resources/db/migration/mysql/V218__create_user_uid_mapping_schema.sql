-- ============================================================
-- 用户 Aloudata UID 映射表
-- ============================================================
-- 从外部用户系统（MySQL/PostgreSQL 源库）定时同步
-- 「登录名 + 租户 → Aloudata UID」映射关系，
-- 用户问数时可使用映射的 UID 认证，无需手动绑定查询账号；
-- 手动绑定（dataagent_datasource_account）优先，本表映射兜底。
-- 唯一键：(username, tenant_id)，同一租户下多个数据源共享一份映射。
-- 登录名大小写不敏感由应用层归一化（转大写后落库）保证，不在排序规则层做；
-- 租户 ID 为业务标识符，按精确匹配设计——二进制排序规则与 PG（默认大小写敏感）行为一致，
-- 避免 ci 排序规则下两环境命中结果不同。
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_user_uid_mapping` (
    `id`           BIGINT       NOT NULL COMMENT '主键 ID',
    `username`     VARCHAR(128) NOT NULL COMMENT '登录名（对齐 mate_user.username）',
    `tenant_id`    VARCHAR(128) NOT NULL COMMENT 'Aloudata 租户 ID（对齐数据源租户配置）',
    `nickname`     VARCHAR(128)          DEFAULT NULL COMMENT '昵称（仅展示用，源库提供，缺失时回落 mate_user.nickname）',
    `aloudata_uid` VARCHAR(500) NOT NULL COMMENT 'Aloudata UID 认证值（AES 加密存储）',
    `status`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0-停用 / 1-启用',
    `sync_source`  VARCHAR(32)           DEFAULT NULL COMMENT '映射来源：jdbc_sync-定时同步 / manual-手动录入',
    `sync_time`    DATETIME              DEFAULT NULL COMMENT '最近同步时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username_tenant` (`username`, `tenant_id`),
    KEY `idx_uid_mapping_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户 Aloudata UID 映射表';
