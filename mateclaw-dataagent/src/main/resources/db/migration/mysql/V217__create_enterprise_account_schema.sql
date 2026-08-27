-- ============================================================
-- 企业认证影子账号映射表（平安领航 SSO 集成）
-- ============================================================
-- 企业认证（账密代验）通过后，首次登录在 mate_user 表自动开通影子账号
-- （随机密码、不可本地登录），本表记录本地账号与企业身份的映射关系，
-- 用于运维审计与后续离职禁用联动。
-- 注意：不修改 mateclaw-server 的既有表结构，本迁移归属 DataAgent 自有迁移集。
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_enterprise_account` (
    `id`             BIGINT       NOT NULL COMMENT '主键 ID',
    `username`       VARCHAR(64)  NOT NULL COMMENT '本地影子账号用户名（= 域账号）',
    `principal_name` VARCHAR(128) NOT NULL COMMENT '企业侧唯一标识（领航 PRINCIPAL_NAME）',
    `source`         VARCHAR(32)  NOT NULL DEFAULT 'PILOT' COMMENT '身份来源：PILOT（领航 UM/AD）',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `last_login_at`  DATETIME              DEFAULT NULL COMMENT '最近企业登录时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_username` (`username`),
    KEY `idx_enterprise_principal` (`principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业认证影子账号映射表';
