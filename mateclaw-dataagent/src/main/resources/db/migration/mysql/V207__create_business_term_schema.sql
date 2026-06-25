-- ============================================================
-- 业务术语表：术语与同义词统一管理
-- ============================================================
-- 术语（Term）是跨数据源的业务概念统一定义，
-- 同义词（Synonym）作为术语的附属属性，以逗号分隔字段存储。
-- 支持租户隔离、类目分组、层级结构（parent_id）、向量化语义检索。

CREATE TABLE IF NOT EXISTS `dataagent_business_term` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `tenant_code`       VARCHAR(64)  NOT NULL COMMENT '租户编码（区分不同业务域）',
    `term_name`         VARCHAR(128) NOT NULL COMMENT '术语名称（主术语/标准名）',
    `synonyms`          VARCHAR(500)          DEFAULT NULL COMMENT '同义词（逗号分隔，如"营收,收入"）',
    `description`       TEXT                  DEFAULT NULL COMMENT '术语定义/解释',
    `category`          VARCHAR(64)           DEFAULT NULL COMMENT '分类（如：财务类、客户类）',
    `parent_id`         BIGINT                DEFAULT NULL COMMENT '父术语 ID（支持层级结构，顶级为 NULL）',
    `embedding_text`    TEXT                  DEFAULT NULL COMMENT '嵌入文本（用于生成向量）',
    `embedding`         BLOB                  DEFAULT NULL COMMENT '向量数据（float32小端序序列化）',
    `embedding_model_id` BIGINT               DEFAULT NULL COMMENT '嵌入模型 ID',
    `status`            INT          NOT NULL DEFAULT 1 COMMENT '状态：0-停用 / 1-启用',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_term_name` (`tenant_code`, `term_name`, `deleted`),
    KEY `idx_tenant_code` (`tenant_code`),
    KEY `idx_category` (`tenant_code`, `category`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务术语表';
