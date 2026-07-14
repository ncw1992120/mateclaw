-- ============================================================
-- DataAgent 洞察仪表盘：低代码仪表盘 Schema 存储
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- schema_json：仪表盘组件配置 JSON（components 数组）
-- agent_id：AI 解读报告使用的 Agent ID
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_insight_dashboard` (
    `id`             BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `name`           VARCHAR(200) NOT NULL COMMENT '仪表盘名称',
    `description`    VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `schema_json`    LONGTEXT     NOT NULL COMMENT '仪表盘 Schema JSON（components 数组）',
    `report_content` MEDIUMTEXT            DEFAULT NULL COMMENT 'AI 分析报告内容（HTML 格式）',
    `status`         VARCHAR(20)           DEFAULT 'draft' COMMENT '状态：draft/published',
    `agent_id`       BIGINT                DEFAULT NULL COMMENT 'AI 解读使用的 Agent ID',
    `owner_id`       BIGINT                DEFAULT NULL COMMENT '所有者用户 ID',
    `owner_name`     VARCHAR(100)          DEFAULT NULL COMMENT '负责人名称',
    `modifier`       VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_insight_workspace_id` (`workspace_id`),
    KEY `idx_insight_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察仪表盘表';
