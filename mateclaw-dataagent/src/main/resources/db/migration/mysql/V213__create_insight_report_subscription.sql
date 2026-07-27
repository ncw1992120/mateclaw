-- ============================================================
-- DataAgent 洞察报告订阅：用户订阅已发布的报告
-- ============================================================
-- report_id：关联的报告 ID
-- user_id：订阅用户 ID
-- workspace_id：所属工作区 ID，资源隔离用
-- 唯一索引 (report_id, user_id)：防止同一用户重复订阅同一报告
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_insight_report_subscription` (
    `id`            BIGINT   NOT NULL COMMENT '主键 ID',
    `report_id`     BIGINT   NOT NULL COMMENT '报告 ID',
    `user_id`       BIGINT   NOT NULL COMMENT '订阅用户 ID',
    `workspace_id`  BIGINT   NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`       INT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_user` (`report_id`, `user_id`),
    KEY `idx_subscription_workspace_id` (`workspace_id`),
    KEY `idx_subscription_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察报告订阅表';
