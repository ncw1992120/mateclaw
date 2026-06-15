-- ============================================================
-- DataAgent 帮助中心功能：文档反馈表
-- ============================================================

-- 1. 帮助文档反馈表
CREATE TABLE IF NOT EXISTS `dataagent_help_feedback` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `document_id`       BIGINT       NOT NULL COMMENT '文档 ID',
    `rating`            INT                   DEFAULT NULL COMMENT '评分（1-5）',
    `suggestion`        VARCHAR(1000)         DEFAULT NULL COMMENT '改进建议',
    `user_id`           VARCHAR(100)          DEFAULT NULL COMMENT '用户标识',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_help_feedback_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帮助文档反馈表';
