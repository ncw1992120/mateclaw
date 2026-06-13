-- ============================================================
-- DataAgent 帮助中心功能：帮助文档分类表 + 帮助文档表
-- ============================================================

-- 1. 帮助文档分类表
CREATE TABLE IF NOT EXISTS `dataagent_help_category` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `name`              VARCHAR(200) NOT NULL COMMENT '分类名称',
    `parent_id`         BIGINT                DEFAULT 0 COMMENT '父分类 ID（0 表示顶级分类）',
    `sort_order`        INT                   DEFAULT 0 COMMENT '排序序号（升序）',
    `icon`              VARCHAR(100)          DEFAULT NULL COMMENT '分类图标（emoji 或 URL）',
    `description`       VARCHAR(500)          DEFAULT NULL COMMENT '分类描述',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_help_category_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帮助文档分类表';

-- 2. 帮助文档表
CREATE TABLE IF NOT EXISTS `dataagent_help_document` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `category_id`       BIGINT       NOT NULL COMMENT '所属分类 ID',
    `title`             VARCHAR(200) NOT NULL COMMENT '文档标题',
    `content`           MEDIUMTEXT            DEFAULT NULL COMMENT '文档内容（Markdown 格式）',
    `sort_order`        INT                   DEFAULT 0 COMMENT '排序序号（升序）',
    `status`            VARCHAR(20)           DEFAULT 'draft' COMMENT '文档状态：draft/published',
    `author`            VARCHAR(100)          DEFAULT NULL COMMENT '作者',
    `view_count`        INT                   DEFAULT 0 COMMENT '浏览次数',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_help_doc_category` (`category_id`),
    KEY `idx_help_doc_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帮助文档表';
