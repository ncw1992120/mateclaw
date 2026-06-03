-- ============================================================
-- DataAgent 数据集功能：数据集主表 + 数据集字段表
-- ============================================================

-- 1. 数据集主表
CREATE TABLE IF NOT EXISTS `dataagent_dataset` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `name`              VARCHAR(200) NOT NULL COMMENT '数据集名称',
    `description`       VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `datasource_id`     BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `datasource_name`   VARCHAR(200)          DEFAULT NULL COMMENT '数据源名称（冗余存储）',
    `table_ids`         TEXT                  DEFAULT NULL COMMENT '关联的数据源表 ID（逗号分隔）',
    `table_names`       TEXT                  DEFAULT NULL COMMENT '关联的数据源表名（逗号分隔）',
    `status`            VARCHAR(20)           DEFAULT 'draft' COMMENT '数据集状态：draft/ready/error',
    `row_count`         BIGINT                DEFAULT 0 COMMENT '行数',
    `column_count`      INT                   DEFAULT 0 COMMENT '列数',
    `owner`             VARCHAR(100)          DEFAULT NULL COMMENT '所有者',
    `modifier`          VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_dataset_datasource_id` (`datasource_id`),
    KEY `idx_dataset_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集主表';

-- 2. 数据集字段表
CREATE TABLE IF NOT EXISTS `dataagent_dataset_field` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `dataset_id`        BIGINT       NOT NULL COMMENT '所属数据集 ID',
    `column_name`       VARCHAR(255) NOT NULL COMMENT '原始列名',
    `column_alias`      VARCHAR(255)          DEFAULT NULL COMMENT '字段别名',
    `column_comment`    VARCHAR(500)          DEFAULT NULL COMMENT '字段注释',
    `data_type`         VARCHAR(100) NOT NULL COMMENT '数据类型',
    `column_size`       INT                   DEFAULT NULL COMMENT '字段大小',
    `decimal_digits`    INT                   DEFAULT NULL COMMENT '小数位数',
    `field_category`    VARCHAR(20)           DEFAULT 'dimension' COMMENT '字段分类：dimension/measure',
    `primary_key`       TINYINT(1)            DEFAULT 0 COMMENT '是否主键',
    `nullable`          TINYINT(1)            DEFAULT 1 COMMENT '是否可空',
    `default_value`     VARCHAR(500)          DEFAULT NULL COMMENT '默认值',
    `ordinal_position`  INT                   DEFAULT NULL COMMENT '排序位置',
    `datasource_id`     BIGINT                DEFAULT NULL COMMENT '来源数据源 ID',
    `source_table_id`   BIGINT                DEFAULT NULL COMMENT '来源表 ID',
    `source_table_name` VARCHAR(255)          DEFAULT NULL COMMENT '来源表名',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_field_dataset_id` (`dataset_id`),
    KEY `idx_field_category` (`dataset_id`, `field_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集字段表';
