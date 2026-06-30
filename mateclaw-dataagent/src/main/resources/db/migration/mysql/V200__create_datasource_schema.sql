-- ============================================================
-- DataAgent 多源异构数据接入：数据源、表元数据、字段元数据
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- owner_id：数据源创建者用户 ID，列表查询按此过滤（不同用户仅可见自己配置的数据源）
-- ============================================================

-- 1. 数据源主表
CREATE TABLE IF NOT EXISTS `dataagent_datasource` (
    `id`                        BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`              BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `owner_id`                  BIGINT                DEFAULT NULL COMMENT '数据源创建者用户 ID（权限隔离用，列表查询按此过滤）',
    `name`                      VARCHAR(200) NOT NULL COMMENT '数据源名称',
    `description`               VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `source_type`               VARCHAR(50)  NOT NULL COMMENT '数据源类型：mysql/postgresql/oracle/snowflake/bigquery/redshift/clickhouse/doris/mongodb/elasticsearch/csv/excel/parquet/api/kafka',
    `host`                      VARCHAR(255)          DEFAULT NULL COMMENT '主机地址（通用字段，可作为历史数据兜底）',
    `product_host`              VARCHAR(255)          DEFAULT NULL COMMENT '产品层服务地址（Aloudata anymetrics，端口默认 8083）',
    `semantic_host`             VARCHAR(255)          DEFAULT NULL COMMENT '语义层服务地址（Aloudata semantic，端口默认 8085）',
    `port`                      INT                   DEFAULT NULL COMMENT '端口',
    `database_name`             VARCHAR(255)          DEFAULT NULL COMMENT '数据库名称/文件路径/接口地址/Topic',
    `username`                  VARCHAR(200)          DEFAULT NULL COMMENT '用户名',
    `password`                  VARCHAR(500)          DEFAULT NULL COMMENT '密码（AES 加密存储）',
    `connection_params`         TEXT                  DEFAULT NULL COMMENT '连接参数（JSON 格式）',
    `schema_name`               VARCHAR(200)          DEFAULT NULL COMMENT 'Schema 名称',
    `enabled`                   TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用',
    `last_test_time`            DATETIME              DEFAULT NULL COMMENT '最近测试时间',
    `last_test_ok`              TINYINT(1)            DEFAULT NULL COMMENT '最近测试结果',
    `schema_status`             VARCHAR(20)           DEFAULT 'pending' COMMENT 'Schema 发现状态：pending/running/completed/failed',
    `last_schema_discovery_time` DATETIME             DEFAULT NULL COMMENT '最近 Schema 发现时间',
    `create_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                   INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_datasource_workspace_id` (`workspace_id`),
    KEY `idx_datasource_owner_id` (`owner_id`),
    KEY `idx_datasource_source_type` (`source_type`),
    KEY `idx_datasource_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源主表';

-- 2. 数据源表元数据
CREATE TABLE IF NOT EXISTS `dataagent_datasource_tables` (
    `id`              BIGINT       NOT NULL COMMENT '主键 ID',
    `datasource_id`   BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `table_name`      VARCHAR(255) NOT NULL COMMENT '表名',
    `table_comment`   VARCHAR(500)          DEFAULT NULL COMMENT '表注释/说明',
    `table_type`      VARCHAR(30)           DEFAULT 'table' COMMENT '表类型：table/view/materialized_view/external',
    `row_count`       BIGINT                DEFAULT NULL COMMENT '估算行数',
    `data_size_bytes` BIGINT                DEFAULT NULL COMMENT '估算数据大小（字节）',
    `schema_name`     VARCHAR(200)          DEFAULT NULL COMMENT 'Schema 名称',
    `engine`          VARCHAR(100)          DEFAULT NULL COMMENT '引擎/存储类型',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_table_datasource_id` (`datasource_id`),
    KEY `idx_table_name` (`datasource_id`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源表元数据';

-- 3. 数据源字段元数据
CREATE TABLE IF NOT EXISTS `dataagent_datasource_columns` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `datasource_id`     BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `table_id`          BIGINT       NOT NULL COMMENT '关联表 ID',
    `column_name`       VARCHAR(255) NOT NULL COMMENT '字段名',
    `column_comment`    VARCHAR(500)          DEFAULT NULL COMMENT '字段注释',
    `data_type`         VARCHAR(100) NOT NULL COMMENT '字段数据类型',
    `column_size`       INT                   DEFAULT NULL COMMENT '字段长度',
    `decimal_digits`    INT                   DEFAULT NULL COMMENT '小数位数',
    `nullable`          TINYINT(1)            DEFAULT 1 COMMENT '是否可为空',
    `primary_key`       TINYINT(1)            DEFAULT 0 COMMENT '是否为主键',
    `indexed`           TINYINT(1)            DEFAULT 0 COMMENT '是否为索引字段',
    `default_value`     VARCHAR(500)          DEFAULT NULL COMMENT '默认值',
    `ordinal_position`  INT                   DEFAULT NULL COMMENT '排序位置',
    `foreign_key_table`  VARCHAR(255)         DEFAULT NULL COMMENT '外键关联表名',
    `foreign_key_column` VARCHAR(255)         DEFAULT NULL COMMENT '外键关联字段名',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_column_datasource_id` (`datasource_id`),
    KEY `idx_column_table_id` (`table_id`),
    KEY `idx_column_name` (`table_id`, `column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源字段元数据';
