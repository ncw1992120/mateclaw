-- ============================================================
-- DataAgent 洞察报告：已发布报告独立存储
-- ============================================================
-- dashboard_id：关联的仪表盘 ID，报告内容从仪表盘复制而来
-- workspace_id：所属工作区 ID，资源隔离用
-- report_content：报告 HTML 内容（从仪表盘 report_content 字段复制）
-- status：报告状态 draft/published
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_insight_report` (
    `id`             BIGINT       NOT NULL COMMENT '主键 ID',
    `dashboard_id`   BIGINT       NOT NULL COMMENT '关联的仪表盘 ID',
    `workspace_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `name`           VARCHAR(200) NOT NULL COMMENT '报告名称',
    `description`    VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `report_content` MEDIUMTEXT            DEFAULT NULL COMMENT '报告 HTML 内容',
    `echarts_options` MEDIUMTEXT           DEFAULT NULL COMMENT 'ECharts option 数据（JSON 格式）',
    `status`         VARCHAR(20)           DEFAULT 'draft' COMMENT '状态：draft/published',
    `owner_id`       BIGINT                DEFAULT NULL COMMENT '所有者用户 ID',
    `owner_name`     VARCHAR(100)          DEFAULT NULL COMMENT '负责人名称',
    `modifier`       VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_report_workspace_id` (`workspace_id`),
    KEY `idx_report_dashboard_id` (`dashboard_id`),
    KEY `idx_report_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='洞察报告表';
