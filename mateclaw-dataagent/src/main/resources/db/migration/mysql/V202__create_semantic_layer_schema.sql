-- ============================================================
-- DataAgent 语义层：字段级语义模型、逻辑外键关系、Schema 嵌入向量
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

-- 1. 字段级语义模型表
CREATE TABLE IF NOT EXISTS `dataagent_semantic_model` (
    `id`                    BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`          BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `datasource_id`         BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `table_name`            VARCHAR(128) NOT NULL COMMENT '表名',
    `column_name`           VARCHAR(128) NOT NULL COMMENT '字段名',
    `business_name`         VARCHAR(200)          DEFAULT NULL COMMENT '业务别名（如"客户满意度分数"）',
    `business_description`  VARCHAR(500)          DEFAULT NULL COMMENT '业务描述，直接用于 Prompt',
    `synonyms`              VARCHAR(500)          DEFAULT NULL COMMENT '同义词（逗号分隔，如"满意度,客户评分"）',
    `data_type`             VARCHAR(100)          DEFAULT NULL COMMENT '物理数据类型',
    `column_comment`        VARCHAR(500)          DEFAULT NULL COMMENT '数据库原始注释',
    `example_values`        VARCHAR(500)          DEFAULT NULL COMMENT '示例值（逗号分隔）',
    `enum_values`           TEXT                  DEFAULT NULL COMMENT '枚举值 JSON（如 {"0":"待支付","1":"已支付"}）',
    `unit`                  VARCHAR(50)           DEFAULT NULL COMMENT '单位（如 °C、m/s、%）',
    `value_range`           VARCHAR(200)          DEFAULT NULL COMMENT '值域范围（如 0~100）',
    `status`                TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '状态：0-停用 / 1-启用',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_semantic_model` (`datasource_id`, `table_name`, `column_name`, `deleted`),
    KEY `idx_semantic_workspace_id` (`workspace_id`),
    KEY `idx_semantic_datasource_id` (`datasource_id`),
    KEY `idx_semantic_table_name` (`datasource_id`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段级语义模型';

-- 2. 逻辑外键关系表
CREATE TABLE IF NOT EXISTS `dataagent_logical_relation` (
    `id`                    BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`          BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `datasource_id`         BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `source_table_name`     VARCHAR(128) NOT NULL COMMENT '源表名',
    `source_column_name`    VARCHAR(128) NOT NULL COMMENT '源字段名',
    `target_table_name`     VARCHAR(128) NOT NULL COMMENT '目标表名',
    `target_column_name`    VARCHAR(128) NOT NULL COMMENT '目标字段名',
    `relation_type`         VARCHAR(10)           DEFAULT '1:N' COMMENT '关系类型：1:1 / 1:N / N:1',
    `description`           VARCHAR(500)          DEFAULT NULL COMMENT '业务描述（如"订单关联客户"）',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_relation_workspace_id` (`workspace_id`),
    KEY `idx_relation_datasource_id` (`datasource_id`),
    KEY `idx_relation_source_table` (`datasource_id`, `source_table_name`),
    KEY `idx_relation_target_table` (`datasource_id`, `target_table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逻辑外键关系';

-- 3. Schema 嵌入向量表
-- 注：嵌入向量表按 datasource_id 隔离，不加 workspace_id（datasource_id 已有关联），
--     且无 deleted 字段（全量同步时先删后插，不需要软删除）
CREATE TABLE IF NOT EXISTS `dataagent_schema_embedding` (
    `id`                    BIGINT       NOT NULL COMMENT '主键 ID',
    `datasource_id`         BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `table_name`            VARCHAR(128) NOT NULL COMMENT '表名',
    `embedding_text`        TEXT         NOT NULL COMMENT '嵌入输入文本',
    `embedding`             LONGBLOB             DEFAULT NULL COMMENT '向量数据（float32 小端序序列化）',
    `embedding_model_id`    BIGINT                DEFAULT NULL COMMENT '使用的嵌入模型 ID',
    `embedding_text_version` INT         NOT NULL DEFAULT 1 COMMENT '嵌入文本格式版本',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_embedding_datasource_id` (`datasource_id`),
    KEY `idx_embedding_table_name` (`datasource_id`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Schema 嵌入向量';
