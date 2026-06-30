-- ============================================================
-- 数据源用户查询账号绑定表
-- ============================================================
-- 每个用户可以为自己绑定的数据源配置独立的查询账号，
-- 查询时优先使用用户自己的查询账号，而非数据源的管理员同步账号。
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_datasource_account` (
    `id`              BIGINT       NOT NULL COMMENT '主键 ID',
    `datasource_id`   BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `workspace_id`    BIGINT       NOT NULL COMMENT '所属工作区 ID',
    `user_id`         BIGINT       NOT NULL COMMENT '用户 ID',
    `query_username`  VARCHAR(200) NOT NULL COMMENT '查询用户名',
    `query_password`  VARCHAR(500) NOT NULL COMMENT '查询密码（AES 加密存储）',
    `status`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0-停用 / 1-启用',
    `last_test_time`  DATETIME              DEFAULT NULL COMMENT '最近测试时间',
    `last_test_ok`    TINYINT(1)            DEFAULT NULL COMMENT '最近测试结果',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_user` (`datasource_id`, `user_id`, `deleted`),
    KEY `idx_account_workspace_id` (`workspace_id`),
    KEY `idx_account_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源用户查询账号绑定表';
