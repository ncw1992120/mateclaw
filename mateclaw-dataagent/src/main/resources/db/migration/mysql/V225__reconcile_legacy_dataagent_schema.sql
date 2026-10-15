-- V225: 收敛历史版本的 DataAgent 表结构。

-- 该迁移只追加，不修改 V200-V224；所有建表语句均使用 IF NOT EXISTS。

-- 先补齐缺失表和列，再补充非唯一索引。业务唯一索引由单独步骤处理。



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
    `meta_shared`               TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '元数据是否共享（1=同工作区所有用户可见，0=仅 owner 可见）',
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

CREATE TABLE IF NOT EXISTS `dataagent_dataset` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `name`              VARCHAR(200) NOT NULL COMMENT '数据集名称',
    `description`       VARCHAR(500)          DEFAULT NULL COMMENT '描述',
    `datasource_id`     BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `datasource_name`   VARCHAR(200)          DEFAULT NULL COMMENT '数据源名称（冗余存储）',
    `table_ids`         TEXT                  DEFAULT NULL COMMENT '关联的数据源表 ID（逗号分隔）',
    `table_names`       TEXT                  DEFAULT NULL COMMENT '关联的数据源表名（逗号分隔）',
    `status`            VARCHAR(20)           DEFAULT 'draft' COMMENT '数据集状态：draft/ready/error',
    `row_count`         BIGINT                DEFAULT 0 COMMENT '行数',
    `column_count`      INT                   DEFAULT 0 COMMENT '列数',
    `owner_id`          BIGINT                DEFAULT NULL COMMENT '所有者用户 ID',
    `modifier`          VARCHAR(100)          DEFAULT NULL COMMENT '修改人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_dataset_workspace_id` (`workspace_id`),
    KEY `idx_dataset_datasource_id` (`datasource_id`),
    KEY `idx_dataset_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集主表';

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

CREATE TABLE IF NOT EXISTS `dataagent_dataset_data` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `dataset_id`        BIGINT       NOT NULL COMMENT '所属数据集 ID',
    `row_data`          JSON         NOT NULL COMMENT '行数据（JSON 对象，key 为字段名，value 为字段值）',
    `row_hash`          VARCHAR(64)  DEFAULT NULL COMMENT '行数据哈希（用于变更检测）',
    `source_row_number` INT          DEFAULT NULL COMMENT '源表原始行号（用于溯源）',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_data_dataset_id` (`dataset_id`),
    KEY `idx_data_row_hash` (`dataset_id`, `row_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集业务数据表';

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

CREATE TABLE IF NOT EXISTS `dataagent_aloudata_metric` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `datasource_id` bigint NOT NULL COMMENT '关联数据源ID',
    `metric_code` varchar(64) DEFAULT NULL COMMENT '指标编码（系统内部唯一标识）',
    `metric_name` varchar(128) NOT NULL COMMENT '指标英文名',
    `metric_display_name` varchar(128) NOT NULL COMMENT '指标展示名（业务别名）',
    `version` int DEFAULT '1' COMMENT '指标版本号',
    `type` varchar(32) NOT NULL COMMENT '指标类型：ATOMIC/DERIVED/COMPOSITE',
    `status` varchar(32) NOT NULL COMMENT '指标终态：ONLINE/OFFLINE',
    `publish_status` varchar(32) DEFAULT NULL COMMENT '发布状态：DRAFT/PUBLISHED',
    `display_status` varchar(32) DEFAULT NULL COMMENT '显示状态：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE',
    `business_caliber` text DEFAULT NULL COMMENT '业务口径描述',
    `owner` varchar(128) DEFAULT NULL COMMENT '指标负责人',
    `business_owner` varchar(128) DEFAULT NULL COMMENT '业务负责人',
    `metric_category_id` varchar(64) DEFAULT NULL COMMENT '指标类目ID',
    `metric_category_name` varchar(128) DEFAULT NULL COMMENT '指标类目名称',
    `unit` varchar(32) DEFAULT NULL COMMENT '指标单位',
    `cn_unit` varchar(32) DEFAULT NULL COMMENT '中文指标单位',
    `metric_view_count` int DEFAULT '0' COMMENT '指标查询次数',
    `time_granularity` varchar(32) DEFAULT NULL COMMENT '时间粒度（数据统计的时间单位）',
    `has_date_limit` tinyint(1) DEFAULT '0' COMMENT '是否有日期限制：0-否，1-是',
    `has_derivation_method` tinyint(1) DEFAULT '0' COMMENT '是否有衍生方法：0-否，1-是',
    `metric_time_data_type` varchar(32) DEFAULT NULL COMMENT '指标时间数据类型：DATE_TIME',
    `can_edit` tinyint(1) DEFAULT '1' COMMENT '是否允许编辑：0-否，1-是',
    `can_delete` tinyint(1) DEFAULT '0' COMMENT '是否允许删除：0-否，1-是',
    `can_usage` tinyint(1) DEFAULT '1' COMMENT '是否允许使用：0-否，1-是',
    `can_auth` tinyint(1) DEFAULT '0' COMMENT '是否允许授权：0-否，1-是',
    `can_transfer` tinyint(1) DEFAULT '0' COMMENT '是否允许转移：0-否，1-是',
    `properties` text DEFAULT NULL COMMENT '指标属性JSON（MANAGE/BUSINESS/TECHNOLOGY/BASE）',
    `gmt_create` varchar(20) DEFAULT NULL COMMENT '创建时间（Aloudata原始格式）',
    `gmt_update` varchar(20) DEFAULT NULL COMMENT '修改时间（Aloudata原始格式）',
    `synonyms` text DEFAULT NULL COMMENT '同义词（逗号分隔）',
    `embedding_text` text DEFAULT NULL COMMENT '嵌入文本（用于生成向量）',
    `embedding` blob DEFAULT NULL COMMENT '向量数据（float32小端序序列化）',
    `embedding_model_id` bigint DEFAULT NULL COMMENT '嵌入模型ID',
    `sync_version` int DEFAULT '0' COMMENT '同步版本号（每次全量同步递增）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_metric_name` (`metric_name`),
    KEY `idx_metric_category_id` (`metric_category_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_sync_version` (`sync_version`),
    KEY `idx_metric_ds_category` (`datasource_id`, `metric_category_id`),
    UNIQUE KEY `uk_metric_ds_name` (`datasource_id`, `metric_name`),
    KEY `idx_metric_keyword` (`datasource_id`, `metric_name`, `metric_display_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Aloudata指标元数据表';

CREATE TABLE IF NOT EXISTS `dataagent_aloudata_dimension` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `datasource_id` bigint NOT NULL COMMENT '关联数据源ID',
    `dim_name` varchar(128) NOT NULL COMMENT '维度名称（租户下唯一）',
    `dim_code` varchar(64) DEFAULT NULL COMMENT '维度编码',
    `dim_display_name` varchar(150) NOT NULL COMMENT '维度中文名',
    `dim_category_id` varchar(64) DEFAULT NULL COMMENT '维度类目ID（未分类为-1）',
    `dim_category_name` varchar(128) DEFAULT NULL COMMENT '维度类目名称',
    `dim_description` text DEFAULT NULL COMMENT '维度描述',
    `dataset_name` varchar(128) DEFAULT NULL COMMENT '数据集名称',
    `origin_data_type` varchar(32) DEFAULT NULL COMMENT '原始数据类型（如VARCHAR）',
    `display_status` varchar(32) DEFAULT NULL COMMENT '维度状态：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE',
    `config_type` varchar(32) DEFAULT NULL COMMENT '维度类型：COLUMN_BIND/CUSTOM',
    `config_value` text DEFAULT NULL COMMENT '列名或自定义表达式',
    `is_time_dimension` tinyint(1) DEFAULT '0' COMMENT '是否时间维度：0-否，1-是',
    `synonyms` text DEFAULT NULL COMMENT '同义词（逗号分隔）',
    `example_values` text DEFAULT NULL COMMENT '示例值（逗号分隔，低基数维度）',
    `embedding_text` text DEFAULT NULL COMMENT '嵌入文本（用于生成向量）',
    `embedding` blob DEFAULT NULL COMMENT '向量数据（float32小端序序列化）',
    `embedding_model_id` bigint DEFAULT NULL COMMENT '嵌入模型ID',
    `sync_version` int DEFAULT '0' COMMENT '同步版本号（每次全量同步递增）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dim_ds_name` (`datasource_id`, `dim_name`),
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_dim_name` (`dim_name`),
    KEY `idx_dim_category_id` (`dim_category_id`),
    KEY `idx_dataset_name` (`dataset_name`),
    KEY `idx_sync_version` (`sync_version`),
    KEY `idx_dim_ds_category` (`datasource_id`, `dim_category_id`),
    KEY `idx_dim_keyword` (`datasource_id`, `dim_name`, `dim_display_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Aloudata维度元数据表';

CREATE TABLE IF NOT EXISTS `dataagent_aloudata_metric_dimension` (
    `id`                    BIGINT       NOT NULL COMMENT '主键 ID',
    `datasource_id`         BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `metric_name`           VARCHAR(200) NOT NULL COMMENT '指标英文名',
    `dim_name`              VARCHAR(200) NOT NULL COMMENT '维度英文名',
    `dim_display_name`      VARCHAR(200)          DEFAULT NULL COMMENT '维度展示名',
    `origin_data_type`      VARCHAR(100)          DEFAULT NULL COMMENT '维度数据类型',
    `sync_version`          INT          NOT NULL DEFAULT 1 COMMENT '同步版本号',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metric_dim` (`datasource_id`, `metric_name`, `dim_name`),
    KEY `idx_md_metric` (`datasource_id`, `metric_name`),
    KEY `idx_md_dim` (`datasource_id`, `dim_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标-维度关联关系';

CREATE TABLE IF NOT EXISTS `dataagent_aloudata_category` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `datasource_id`     BIGINT       NOT NULL COMMENT '关联数据源 ID',
    `category_id`       VARCHAR(100) NOT NULL COMMENT '类目 ID（Aloudata 平台标识）',
    `category_name`     VARCHAR(200)          DEFAULT NULL COMMENT '类目名称',
    `category_type`     VARCHAR(50)           DEFAULT NULL COMMENT '类目类型：CATEGORY_METRIC/CATEGORY_DIMENSION/CATEGORY_DATASET',
    `parent_id`         VARCHAR(100)          DEFAULT NULL COMMENT '父级类目 ID',
    `front_id`          VARCHAR(100)          DEFAULT NULL COMMENT '上级类目 ID（frontId）',
    `type`              VARCHAR(50)           DEFAULT NULL COMMENT '类型：SYSTEM（系统类目）/ null（用户自定义）',
    `sync_version`      INT          NOT NULL DEFAULT 1 COMMENT '同步版本号',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category` (`datasource_id`, `category_id`),
    KEY `idx_category_datasource_id` (`datasource_id`),
    KEY `idx_category_type` (`datasource_id`, `category_type`),
    KEY `idx_category_ds_type_id` (`datasource_id`, `category_type`, `category_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Aloudata 类目元数据';

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

CREATE TABLE IF NOT EXISTS `dataagent_help_document` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `category_id`       BIGINT       NOT NULL COMMENT '所属分类 ID',
    `title`             VARCHAR(200) NOT NULL COMMENT '文档标题',
    `content`           MEDIUMTEXT            DEFAULT NULL COMMENT '文档内容（Markdown 格式）',
    `summary`           VARCHAR(500)          DEFAULT NULL COMMENT '文档摘要',
    `sort_order`        INT                   DEFAULT 0 COMMENT '排序序号（升序）',
    `status`            VARCHAR(20)           DEFAULT 'draft' COMMENT '文档状态：draft/published',
    `author`            VARCHAR(100)          DEFAULT NULL COMMENT '作者',
    `tags`              VARCHAR(500)          DEFAULT NULL COMMENT '标签（逗号分隔）',
    `view_count`        INT                   DEFAULT 0 COMMENT '浏览次数',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_help_doc_workspace_id` (`workspace_id`),
    KEY `idx_help_doc_category` (`category_id`),
    KEY `idx_help_doc_status` (`status`),
    FULLTEXT KEY `ft_help_doc_search` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帮助文档表';

CREATE TABLE IF NOT EXISTS `dataagent_help_feedback` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `document_id`       BIGINT       NOT NULL COMMENT '文档 ID',
    `rating`            INT                   DEFAULT NULL COMMENT '评分（1-5）',
    `suggestion`        VARCHAR(1000)         DEFAULT NULL COMMENT '改进建议',
    `user_id`           BIGINT                DEFAULT NULL COMMENT '用户 ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_help_feedback_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帮助文档反馈表';

CREATE TABLE IF NOT EXISTS `dataagent_business_term` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `workspace_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '所属工作区 ID',
    `tenant_code`       VARCHAR(64)  NOT NULL COMMENT '租户编码（区分不同业务域）',
    `term_name`         VARCHAR(128) NOT NULL COMMENT '术语名称（主术语/标准名）',
    `synonyms`          VARCHAR(500)          DEFAULT NULL COMMENT '同义词（逗号分隔，如"营收,收入"）',
    `description`       TEXT                  DEFAULT NULL COMMENT '术语定义/解释',
    `calculation_formula` TEXT                DEFAULT NULL COMMENT '计算公式（描述该术语的指标计算逻辑/表达式）',
    `data_caliber`      TEXT                  DEFAULT NULL COMMENT '数据口径（统计范围、边界条件、排除规则等）',
    `data_source`       VARCHAR(256)          DEFAULT NULL COMMENT '数据来源/源系统（如CRM、ERP等）',
    `owner`             VARCHAR(128)          DEFAULT NULL COMMENT '责任人/归属部门（负责维护该术语定义的准确性）',
    `business_rule`     TEXT                  DEFAULT NULL COMMENT '业务规则（约束条件/业务逻辑规则）',
    `related_terms`     VARCHAR(500)          DEFAULT NULL COMMENT '关联术语ID（逗号分隔，如"101,102"）',
    `related_metrics_json`    TEXT COMMENT '关联指标引用JSON（[{"id":1,"datasourceId":1,"datasourceName":"CRM","name":"sales_amount","displayName":"销售额"}]）',
    `related_dimensions_json` TEXT COMMENT '关联维度引用JSON（[{"id":1,"datasourceId":1,"datasourceName":"CRM","name":"province","displayName":"省份"}]）',
    `example`           TEXT                  DEFAULT NULL COMMENT '示例/用例（该术语在实际业务中的使用示例）',
    `security_level`    VARCHAR(32)           DEFAULT NULL COMMENT '安全分级（公开/内部/机密）',
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
    KEY `idx_term_workspace_id` (`workspace_id`),
    KEY `idx_tenant_code` (`tenant_code`),
    KEY `idx_category` (`tenant_code`, `category`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务术语表';

CREATE TABLE IF NOT EXISTS `dataagent_resource_grant` (
    `id`              BIGINT       NOT NULL                    COMMENT '主键（雪花ID）',
    `resource_type`   VARCHAR(64)  NOT NULL                    COMMENT '资源类型：skill / agent / datasource / business_term 等',
    `resource_id`     BIGINT       NOT NULL                    COMMENT '资源 ID（对应业务表的主键）',
    `workspace_id`    BIGINT       NOT NULL    DEFAULT 1       COMMENT '所属工作区 ID',
    `grant_type`      VARCHAR(32)  NOT NULL                    COMMENT '授权类型：role / user / group（按角色/用户/用户组授权）',
    `grantee_id`      VARCHAR(128) NOT NULL                    COMMENT '被授权者标识：角色名/用户ID/用户组ID',
    `permission`      VARCHAR(32)  NOT NULL    DEFAULT 'use'   COMMENT '权限：view / use / edit（查看/使用/编辑）',
    `granted_by`      BIGINT       NULL                        COMMENT '授权人用户 ID',
    `status`          TINYINT      NOT NULL    DEFAULT 1       COMMENT '状态：0-已撤销 / 1-生效中',
    `expire_time`     DATETIME     NULL                        COMMENT '过期时间（NULL 表示永久）',
    `create_time`     DATETIME     NOT NULL                    COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL                    COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL    DEFAULT 0       COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resource_grant` (`resource_type`, `resource_id`, `grant_type`, `grantee_id`, `permission`, `deleted`),
    KEY `idx_workspace_resource` (`workspace_id`, `resource_type`, `resource_id`),
    KEY `idx_grantee` (`grant_type`, `grantee_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用资源授权表';

CREATE TABLE IF NOT EXISTS `dataagent_approval_record` (
    `id`              BIGINT       NOT NULL                    COMMENT '主键（雪花ID）',
    `approval_type`   VARCHAR(64)  NOT NULL                    COMMENT '审批类型：skill_publish / agent_publish / resource_grant 等',
    `resource_type`   VARCHAR(64)  NOT NULL                    COMMENT '资源类型：skill / agent / datasource 等',
    `resource_id`     BIGINT       NOT NULL                    COMMENT '资源 ID',
    `resource_name`   VARCHAR(255) NULL                        COMMENT '资源名称（冗余，便于展示）',
    `workspace_id`    BIGINT       NOT NULL    DEFAULT 1       COMMENT '所属工作区 ID',
    `requester_id`    BIGINT       NOT NULL                    COMMENT '申请人用户 ID',
    `requester_name`  VARCHAR(128) NULL                        COMMENT '申请人名称（冗余）',
    `action`          VARCHAR(32)  NOT NULL                    COMMENT '申请动作：publish / grant / delete 等',
    `payload_json`    TEXT         NULL                        COMMENT '申请负载（JSON，存储审批所需的额外信息）',
    `status`          VARCHAR(16)  NOT NULL    DEFAULT 'pending' COMMENT '状态：pending / approved / rejected / cancelled',
    `current_step`    INT          NOT NULL    DEFAULT 0       COMMENT '当前审批步骤（0=初始，多级审批时递增）',
    `approver_id`     BIGINT       NULL                        COMMENT '审批人用户 ID（最终审批者）',
    `approver_name`   VARCHAR(128) NULL                        COMMENT '审批人名称（冗余）',
    `comment`         TEXT         NULL                        COMMENT '审批意见',
    `submitted_at`    DATETIME     NOT NULL                    COMMENT '提交时间',
    `approved_at`     DATETIME     NULL                        COMMENT '审批完成时间',
    `create_time`     DATETIME     NOT NULL                    COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL                    COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL    DEFAULT 0       COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_workspace_status` (`workspace_id`, `status`, `deleted`),
    KEY `idx_requester` (`requester_id`, `status`, `deleted`),
    KEY `idx_resource` (`resource_type`, `resource_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程记录表';

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

CREATE TABLE IF NOT EXISTS `dataagent_query_state` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `conversation_id`   VARCHAR(128) NOT NULL COMMENT '会话 ID',
    `datasource_id`     BIGINT       NOT NULL COMMENT '数据源 ID',
    `metrics`           VARCHAR(4000) DEFAULT NULL COMMENT '指标英文名列表（JSON 数组）',
    `dimensions`        VARCHAR(2000) DEFAULT NULL COMMENT '维度英文名列表（JSON 数组）',
    `time_constraint`   VARCHAR(2000) DEFAULT NULL COMMENT '时间约束表达式',
    `filters`           VARCHAR(4000) DEFAULT NULL COMMENT '全局筛选条件（JSON 数组）',
    `orders`            VARCHAR(2000) DEFAULT NULL COMMENT '排序定义（JSON 数组）',
    `metric_display_map` TEXT COMMENT '指标英文名→中文展示名/口径映射（JSON 对象）',
    `request_json`      TEXT COMMENT '成功请求的完整参数 JSON（审计/追踪）',
    `query_count`       INT          NOT NULL DEFAULT 1 COMMENT '该基座被复用的次数',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dataagent_query_state_conv_ds` (`conversation_id`, `datasource_id`),
    KEY `idx_dataagent_query_state_conversation` (`conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='会话级成功指标查询基座（多轮追问结构化状态）';

CREATE TABLE IF NOT EXISTS `dataagent_enterprise_account` (
    `id`             BIGINT       NOT NULL COMMENT '主键 ID',
    `username`       VARCHAR(64)  NOT NULL COMMENT '本地影子账号用户名（= 域账号）',
    `principal_name` VARCHAR(128) NOT NULL COMMENT '企业侧唯一标识（领航 PRINCIPAL_NAME）',
    `source`         VARCHAR(32)  NOT NULL DEFAULT 'PILOT' COMMENT '身份来源：PILOT（领航 UM/AD）',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `last_login_at`  DATETIME              DEFAULT NULL COMMENT '最近企业登录时间',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_username` (`username`),
    KEY `idx_enterprise_principal` (`principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业认证影子账号映射表';

CREATE TABLE IF NOT EXISTS `dataagent_dashboard_execution` (
    `id` BIGINT NOT NULL COMMENT '主键（雪花 ID）',
    `execution_id` VARCHAR(128) NOT NULL COMMENT 'Runner 任务 ID',
    `dashboard_id` BIGINT NOT NULL COMMENT '仪表盘 ID',
    `workspace_id` BIGINT NOT NULL COMMENT '工作区 ID',
    `user_id` BIGINT NOT NULL COMMENT '发起用户 ID',
    `status` VARCHAR(32) NOT NULL COMMENT '任务状态',
    `parameters_json` TEXT COMMENT '运行时参数 JSON',
    `output_json` MEDIUMTEXT COMMENT '小结果 JSON（大结果使用 outputRef）',
    `output_ref_json` TEXT COMMENT '大结果 ObjectRef JSON',
    `logs` TEXT COMMENT '受限标准输出日志',
    `error_message` TEXT COMMENT '错误信息',
    `return_code` INT COMMENT 'Runner 进程退出码',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dataagent_dashboard_execution_id` (`execution_id`),
    KEY `idx_dataagent_dashboard_execution_dashboard` (`dashboard_id`, `create_time`),
    KEY `idx_dataagent_dashboard_execution_workspace` (`workspace_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仪表盘 Python 执行记录';

CREATE TABLE IF NOT EXISTS `dataagent_user_uid_mapping` (
    `id`           BIGINT       NOT NULL COMMENT '主键 ID',
    `username`     VARCHAR(128) NOT NULL COMMENT '登录名（对齐 mate_user.username）',
    `tenant_id`    VARCHAR(128) NOT NULL COMMENT 'Aloudata 租户 ID（对齐数据源租户配置）',
    `nickname`     VARCHAR(128)          DEFAULT NULL COMMENT '昵称（仅展示用，源库提供，缺失时回落 mate_user.nickname）',
    `aloudata_uid` VARCHAR(500) NOT NULL COMMENT 'Aloudata UID 认证值（AES 加密存储）',
    `status`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：0-停用 / 1-启用',
    `sync_source`  VARCHAR(32)           DEFAULT NULL COMMENT '映射来源：jdbc_sync-定时同步 / manual-手动录入',
    `sync_time`    DATETIME              DEFAULT NULL COMMENT '最近同步时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username_tenant` (`username`, `tenant_id`),
    KEY `idx_uid_mapping_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户 Aloudata UID 映射表';

-- Legacy columns that were added to CREATE TABLE files after first deployment.
-- Existing values are retained; workspace IDs use the historical default workspace.

SET @v225_dataagent_datasource_product_host_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'product_host'
);
SET @v225_dataagent_datasource_product_host_ddl := IF(@v225_dataagent_datasource_product_host_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `product_host` VARCHAR(255) DEFAULT NULL COMMENT ''产品层服务地址（Aloudata anymetrics，端口默认 8083）''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_product_host_stmt FROM @v225_dataagent_datasource_product_host_ddl;
EXECUTE v225_dataagent_datasource_product_host_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_product_host_stmt;

SET @v225_dataagent_datasource_semantic_host_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'semantic_host'
);
SET @v225_dataagent_datasource_semantic_host_ddl := IF(@v225_dataagent_datasource_semantic_host_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `semantic_host` VARCHAR(255) DEFAULT NULL COMMENT ''语义层服务地址（Aloudata semantic，端口默认 8085）''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_semantic_host_stmt FROM @v225_dataagent_datasource_semantic_host_ddl;
EXECUTE v225_dataagent_datasource_semantic_host_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_semantic_host_stmt;

SET @v225_dataagent_datasource_workspace_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'workspace_id'
);
SET @v225_dataagent_datasource_workspace_id_ddl := IF(@v225_dataagent_datasource_workspace_id_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''所属工作区 ID''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_workspace_id_stmt FROM @v225_dataagent_datasource_workspace_id_ddl;
EXECUTE v225_dataagent_datasource_workspace_id_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_workspace_id_stmt;

SET @v225_dataagent_datasource_owner_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'owner_id'
);
SET @v225_dataagent_datasource_owner_id_ddl := IF(@v225_dataagent_datasource_owner_id_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT ''数据源创建者用户 ID（权限隔离用，列表查询按此字段过滤）''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_owner_id_stmt FROM @v225_dataagent_datasource_owner_id_ddl;
EXECUTE v225_dataagent_datasource_owner_id_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_owner_id_stmt;

SET @v225_dataagent_datasource_meta_shared_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'meta_shared'
);
SET @v225_dataagent_datasource_meta_shared_ddl := IF(@v225_dataagent_datasource_meta_shared_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `meta_shared` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''元数据是否共享（1=同工作区所有用户可见，0=仅 owner 可见）''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_meta_shared_stmt FROM @v225_dataagent_datasource_meta_shared_ddl;
EXECUTE v225_dataagent_datasource_meta_shared_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_meta_shared_stmt;

SET @v225_dataagent_datasource_aloudata_sync_enabled_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'aloudata_sync_enabled'
);
SET @v225_dataagent_datasource_aloudata_sync_enabled_ddl := IF(@v225_dataagent_datasource_aloudata_sync_enabled_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `aloudata_sync_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Aloudata 语义层定时同步开关（1=开启，0=关闭）''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_aloudata_sync_enabled_stmt FROM @v225_dataagent_datasource_aloudata_sync_enabled_ddl;
EXECUTE v225_dataagent_datasource_aloudata_sync_enabled_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_aloudata_sync_enabled_stmt;

SET @v225_dataagent_datasource_aloudata_sync_cron_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'aloudata_sync_cron'
);
SET @v225_dataagent_datasource_aloudata_sync_cron_ddl := IF(@v225_dataagent_datasource_aloudata_sync_cron_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `aloudata_sync_cron` VARCHAR(100) DEFAULT NULL COMMENT ''Aloudata 语义层定时同步 cron 表达式''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_aloudata_sync_cron_stmt FROM @v225_dataagent_datasource_aloudata_sync_cron_ddl;
EXECUTE v225_dataagent_datasource_aloudata_sync_cron_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_aloudata_sync_cron_stmt;

SET @v225_dataagent_datasource_last_aloudata_sync_time_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND COLUMN_NAME = 'last_aloudata_sync_time'
);
SET @v225_dataagent_datasource_last_aloudata_sync_time_ddl := IF(@v225_dataagent_datasource_last_aloudata_sync_time_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD COLUMN `last_aloudata_sync_time` DATETIME DEFAULT NULL COMMENT ''最近一次 Aloudata 语义层同步完成时间''',
    'SELECT 1');
PREPARE v225_dataagent_datasource_last_aloudata_sync_time_stmt FROM @v225_dataagent_datasource_last_aloudata_sync_time_ddl;
EXECUTE v225_dataagent_datasource_last_aloudata_sync_time_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_last_aloudata_sync_time_stmt;

SET @v225_dataagent_dataset_workspace_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dataset' AND COLUMN_NAME = 'workspace_id'
);
SET @v225_dataagent_dataset_workspace_id_ddl := IF(@v225_dataagent_dataset_workspace_id_exists = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''所属工作区 ID''',
    'SELECT 1');
PREPARE v225_dataagent_dataset_workspace_id_stmt FROM @v225_dataagent_dataset_workspace_id_ddl;
EXECUTE v225_dataagent_dataset_workspace_id_stmt;
DEALLOCATE PREPARE v225_dataagent_dataset_workspace_id_stmt;

SET @v225_dataagent_dataset_owner_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dataset' AND COLUMN_NAME = 'owner_id'
);
SET @v225_dataagent_dataset_owner_id_ddl := IF(@v225_dataagent_dataset_owner_id_exists = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT ''所有者用户 ID''',
    'SELECT 1');
PREPARE v225_dataagent_dataset_owner_id_stmt FROM @v225_dataagent_dataset_owner_id_ddl;
EXECUTE v225_dataagent_dataset_owner_id_stmt;
DEALLOCATE PREPARE v225_dataagent_dataset_owner_id_stmt;

SET @v225_dataagent_dataset_source_type_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dataset' AND COLUMN_NAME = 'source_type'
);
SET @v225_dataagent_dataset_source_type_ddl := IF(@v225_dataagent_dataset_source_type_exists = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `source_type` VARCHAR(40) NOT NULL DEFAULT ''JDBC_TABLE'' COMMENT ''统一来源类型：JDBC_TABLE/JDBC_SQL/ALOUDATA_ANALYSIS_VIEW/HTTP_API/FILE''',
    'SELECT 1');
PREPARE v225_dataagent_dataset_source_type_stmt FROM @v225_dataagent_dataset_source_type_ddl;
EXECUTE v225_dataagent_dataset_source_type_stmt;
DEALLOCATE PREPARE v225_dataagent_dataset_source_type_stmt;

SET @v225_dataagent_dataset_source_config_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dataset' AND COLUMN_NAME = 'source_config'
);
SET @v225_dataagent_dataset_source_config_ddl := IF(@v225_dataagent_dataset_source_config_exists = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `source_config` TEXT DEFAULT NULL COMMENT ''来源配置（仅 DataAgent 内部使用）''',
    'SELECT 1');
PREPARE v225_dataagent_dataset_source_config_stmt FROM @v225_dataagent_dataset_source_config_ddl;
EXECUTE v225_dataagent_dataset_source_config_stmt;
DEALLOCATE PREPARE v225_dataagent_dataset_source_config_stmt;

SET @v225_dataagent_dataset_schema_version_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dataset' AND COLUMN_NAME = 'schema_version'
);
SET @v225_dataagent_dataset_schema_version_ddl := IF(@v225_dataagent_dataset_schema_version_exists = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `schema_version` INT NOT NULL DEFAULT 1 COMMENT ''数据集契约版本''',
    'SELECT 1');
PREPARE v225_dataagent_dataset_schema_version_stmt FROM @v225_dataagent_dataset_schema_version_ddl;
EXECUTE v225_dataagent_dataset_schema_version_stmt;
DEALLOCATE PREPARE v225_dataagent_dataset_schema_version_stmt;

SET @v225_dataagent_semantic_model_workspace_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_semantic_model' AND COLUMN_NAME = 'workspace_id'
);
SET @v225_dataagent_semantic_model_workspace_id_ddl := IF(@v225_dataagent_semantic_model_workspace_id_exists = 0,
    'ALTER TABLE `dataagent_semantic_model` ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''所属工作区 ID''',
    'SELECT 1');
PREPARE v225_dataagent_semantic_model_workspace_id_stmt FROM @v225_dataagent_semantic_model_workspace_id_ddl;
EXECUTE v225_dataagent_semantic_model_workspace_id_stmt;
DEALLOCATE PREPARE v225_dataagent_semantic_model_workspace_id_stmt;

SET @v225_dataagent_semantic_model_owner_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_semantic_model' AND COLUMN_NAME = 'owner_id'
);
SET @v225_dataagent_semantic_model_owner_id_ddl := IF(@v225_dataagent_semantic_model_owner_id_exists = 0,
    'ALTER TABLE `dataagent_semantic_model` ADD COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT ''创建者用户ID（资源归属人）''',
    'SELECT 1');
PREPARE v225_dataagent_semantic_model_owner_id_stmt FROM @v225_dataagent_semantic_model_owner_id_ddl;
EXECUTE v225_dataagent_semantic_model_owner_id_stmt;
DEALLOCATE PREPARE v225_dataagent_semantic_model_owner_id_stmt;

SET @v225_dataagent_logical_relation_workspace_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_logical_relation' AND COLUMN_NAME = 'workspace_id'
);
SET @v225_dataagent_logical_relation_workspace_id_ddl := IF(@v225_dataagent_logical_relation_workspace_id_exists = 0,
    'ALTER TABLE `dataagent_logical_relation` ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''所属工作区 ID''',
    'SELECT 1');
PREPARE v225_dataagent_logical_relation_workspace_id_stmt FROM @v225_dataagent_logical_relation_workspace_id_ddl;
EXECUTE v225_dataagent_logical_relation_workspace_id_stmt;
DEALLOCATE PREPARE v225_dataagent_logical_relation_workspace_id_stmt;

SET @v225_dataagent_logical_relation_owner_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_logical_relation' AND COLUMN_NAME = 'owner_id'
);
SET @v225_dataagent_logical_relation_owner_id_ddl := IF(@v225_dataagent_logical_relation_owner_id_exists = 0,
    'ALTER TABLE `dataagent_logical_relation` ADD COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT ''创建者用户ID（资源归属人）''',
    'SELECT 1');
PREPARE v225_dataagent_logical_relation_owner_id_stmt FROM @v225_dataagent_logical_relation_owner_id_ddl;
EXECUTE v225_dataagent_logical_relation_owner_id_stmt;
DEALLOCATE PREPARE v225_dataagent_logical_relation_owner_id_stmt;

SET @v225_dataagent_help_document_workspace_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_document' AND COLUMN_NAME = 'workspace_id'
);
SET @v225_dataagent_help_document_workspace_id_ddl := IF(@v225_dataagent_help_document_workspace_id_exists = 0,
    'ALTER TABLE `dataagent_help_document` ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''所属工作区 ID''',
    'SELECT 1');
PREPARE v225_dataagent_help_document_workspace_id_stmt FROM @v225_dataagent_help_document_workspace_id_ddl;
EXECUTE v225_dataagent_help_document_workspace_id_stmt;
DEALLOCATE PREPARE v225_dataagent_help_document_workspace_id_stmt;

SET @v225_dataagent_help_document_summary_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_document' AND COLUMN_NAME = 'summary'
);
SET @v225_dataagent_help_document_summary_ddl := IF(@v225_dataagent_help_document_summary_exists = 0,
    'ALTER TABLE `dataagent_help_document` ADD COLUMN `summary` VARCHAR(500) DEFAULT NULL COMMENT ''文档摘要''',
    'SELECT 1');
PREPARE v225_dataagent_help_document_summary_stmt FROM @v225_dataagent_help_document_summary_ddl;
EXECUTE v225_dataagent_help_document_summary_stmt;
DEALLOCATE PREPARE v225_dataagent_help_document_summary_stmt;

SET @v225_dataagent_help_document_tags_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_document' AND COLUMN_NAME = 'tags'
);
SET @v225_dataagent_help_document_tags_ddl := IF(@v225_dataagent_help_document_tags_exists = 0,
    'ALTER TABLE `dataagent_help_document` ADD COLUMN `tags` VARCHAR(500) DEFAULT NULL COMMENT ''标签（逗号分隔）''',
    'SELECT 1');
PREPARE v225_dataagent_help_document_tags_stmt FROM @v225_dataagent_help_document_tags_ddl;
EXECUTE v225_dataagent_help_document_tags_stmt;
DEALLOCATE PREPARE v225_dataagent_help_document_tags_stmt;

SET @v225_dataagent_business_term_workspace_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'workspace_id'
);
SET @v225_dataagent_business_term_workspace_id_ddl := IF(@v225_dataagent_business_term_workspace_id_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT ''所属工作区 ID''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_workspace_id_stmt FROM @v225_dataagent_business_term_workspace_id_ddl;
EXECUTE v225_dataagent_business_term_workspace_id_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_workspace_id_stmt;

SET @v225_dataagent_business_term_calculation_formula_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'calculation_formula'
);
SET @v225_dataagent_business_term_calculation_formula_ddl := IF(@v225_dataagent_business_term_calculation_formula_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `calculation_formula` TEXT DEFAULT NULL COMMENT ''计算公式''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_calculation_formula_stmt FROM @v225_dataagent_business_term_calculation_formula_ddl;
EXECUTE v225_dataagent_business_term_calculation_formula_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_calculation_formula_stmt;

SET @v225_dataagent_business_term_data_caliber_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'data_caliber'
);
SET @v225_dataagent_business_term_data_caliber_ddl := IF(@v225_dataagent_business_term_data_caliber_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `data_caliber` TEXT DEFAULT NULL COMMENT ''数据口径''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_data_caliber_stmt FROM @v225_dataagent_business_term_data_caliber_ddl;
EXECUTE v225_dataagent_business_term_data_caliber_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_data_caliber_stmt;

SET @v225_dataagent_business_term_data_source_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'data_source'
);
SET @v225_dataagent_business_term_data_source_ddl := IF(@v225_dataagent_business_term_data_source_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `data_source` VARCHAR(256) DEFAULT NULL COMMENT ''数据来源/源系统''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_data_source_stmt FROM @v225_dataagent_business_term_data_source_ddl;
EXECUTE v225_dataagent_business_term_data_source_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_data_source_stmt;

SET @v225_dataagent_business_term_owner_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'owner'
);
SET @v225_dataagent_business_term_owner_ddl := IF(@v225_dataagent_business_term_owner_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `owner` VARCHAR(128) DEFAULT NULL COMMENT ''责任人/归属部门''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_owner_stmt FROM @v225_dataagent_business_term_owner_ddl;
EXECUTE v225_dataagent_business_term_owner_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_owner_stmt;

SET @v225_dataagent_business_term_business_rule_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'business_rule'
);
SET @v225_dataagent_business_term_business_rule_ddl := IF(@v225_dataagent_business_term_business_rule_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `business_rule` TEXT DEFAULT NULL COMMENT ''业务规则''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_business_rule_stmt FROM @v225_dataagent_business_term_business_rule_ddl;
EXECUTE v225_dataagent_business_term_business_rule_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_business_rule_stmt;

SET @v225_dataagent_business_term_related_terms_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'related_terms'
);
SET @v225_dataagent_business_term_related_terms_ddl := IF(@v225_dataagent_business_term_related_terms_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `related_terms` VARCHAR(500) DEFAULT NULL COMMENT ''关联术语ID''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_related_terms_stmt FROM @v225_dataagent_business_term_related_terms_ddl;
EXECUTE v225_dataagent_business_term_related_terms_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_related_terms_stmt;

SET @v225_dataagent_business_term_related_metrics_json_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'related_metrics_json'
);
SET @v225_dataagent_business_term_related_metrics_json_ddl := IF(@v225_dataagent_business_term_related_metrics_json_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `related_metrics_json` TEXT DEFAULT NULL COMMENT ''关联指标引用JSON''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_related_metrics_json_stmt FROM @v225_dataagent_business_term_related_metrics_json_ddl;
EXECUTE v225_dataagent_business_term_related_metrics_json_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_related_metrics_json_stmt;

SET @v225_dataagent_business_term_related_dimensions_json_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'related_dimensions_json'
);
SET @v225_dataagent_business_term_related_dimensions_json_ddl := IF(@v225_dataagent_business_term_related_dimensions_json_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `related_dimensions_json` TEXT DEFAULT NULL COMMENT ''关联维度引用JSON''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_related_dimensions_json_stmt FROM @v225_dataagent_business_term_related_dimensions_json_ddl;
EXECUTE v225_dataagent_business_term_related_dimensions_json_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_related_dimensions_json_stmt;

SET @v225_dataagent_business_term_example_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'example'
);
SET @v225_dataagent_business_term_example_ddl := IF(@v225_dataagent_business_term_example_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `example` TEXT DEFAULT NULL COMMENT ''示例/用例''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_example_stmt FROM @v225_dataagent_business_term_example_ddl;
EXECUTE v225_dataagent_business_term_example_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_example_stmt;

SET @v225_dataagent_business_term_security_level_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND COLUMN_NAME = 'security_level'
);
SET @v225_dataagent_business_term_security_level_ddl := IF(@v225_dataagent_business_term_security_level_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD COLUMN `security_level` VARCHAR(32) DEFAULT NULL COMMENT ''安全分级''',
    'SELECT 1');
PREPARE v225_dataagent_business_term_security_level_stmt FROM @v225_dataagent_business_term_security_level_ddl;
EXECUTE v225_dataagent_business_term_security_level_stmt;
DEALLOCATE PREPARE v225_dataagent_business_term_security_level_stmt;

SET @v225_dataagent_insight_dashboard_report_content_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_insight_dashboard' AND COLUMN_NAME = 'report_content'
);
SET @v225_dataagent_insight_dashboard_report_content_ddl := IF(@v225_dataagent_insight_dashboard_report_content_exists = 0,
    'ALTER TABLE `dataagent_insight_dashboard` ADD COLUMN `report_content` MEDIUMTEXT DEFAULT NULL COMMENT ''AI 分析报告内容（HTML 格式）''',
    'SELECT 1');
PREPARE v225_dataagent_insight_dashboard_report_content_stmt FROM @v225_dataagent_insight_dashboard_report_content_ddl;
EXECUTE v225_dataagent_insight_dashboard_report_content_stmt;
DEALLOCATE PREPARE v225_dataagent_insight_dashboard_report_content_stmt;

UPDATE `dataagent_datasource` SET `workspace_id` = 1 WHERE `workspace_id` IS NULL;
UPDATE `dataagent_dataset` SET `workspace_id` = 1 WHERE `workspace_id` IS NULL;
UPDATE `dataagent_semantic_model` SET `workspace_id` = 1 WHERE `workspace_id` IS NULL;
UPDATE `dataagent_logical_relation` SET `workspace_id` = 1 WHERE `workspace_id` IS NULL;
UPDATE `dataagent_help_document` SET `workspace_id` = 1 WHERE `workspace_id` IS NULL;
UPDATE `dataagent_business_term` SET `workspace_id` = 1 WHERE `workspace_id` IS NULL;

-- Normalize workspace scope columns as well as their values for older nullable schemas.
ALTER TABLE `dataagent_datasource` MODIFY COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID';
ALTER TABLE `dataagent_dataset` MODIFY COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID';
ALTER TABLE `dataagent_semantic_model` MODIFY COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID';
ALTER TABLE `dataagent_logical_relation` MODIFY COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID';
ALTER TABLE `dataagent_help_document` MODIFY COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID';
ALTER TABLE `dataagent_business_term` MODIFY COLUMN `workspace_id` BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID';

-- Preserve historic string feedback IDs before exposing the numeric user_id contract.
SET @v225_feedback_user_id_type := (
    SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_feedback' AND COLUMN_NAME = 'user_id'
);
SET @v225_feedback_legacy_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_feedback' AND COLUMN_NAME = 'legacy_user_id'
);
SET @v225_feedback_rename_ddl := IF(
    @v225_feedback_user_id_type IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext') AND @v225_feedback_legacy_exists = 0,
    'ALTER TABLE `dataagent_help_feedback` CHANGE COLUMN `user_id` `legacy_user_id` VARCHAR(100) DEFAULT NULL COMMENT ''历史用户标识（原字符串）''',
    'SELECT 1'
);
PREPARE v225_feedback_rename_stmt FROM @v225_feedback_rename_ddl;
EXECUTE v225_feedback_rename_stmt;
DEALLOCATE PREPARE v225_feedback_rename_stmt;

-- Existing schemas may already have a numeric user_id and no legacy column.
-- Ensure the preservation column exists before the unconditional backfill below.
SET @v225_dataagent_help_feedback_legacy_user_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_feedback' AND COLUMN_NAME = 'legacy_user_id'
);
SET @v225_dataagent_help_feedback_legacy_user_id_ddl := IF(@v225_dataagent_help_feedback_legacy_user_id_exists = 0,
    'ALTER TABLE `dataagent_help_feedback` ADD COLUMN `legacy_user_id` VARCHAR(100) DEFAULT NULL COMMENT ''历史用户标识（原字符串）''',
    'SELECT 1');
PREPARE v225_dataagent_help_feedback_legacy_user_id_stmt FROM @v225_dataagent_help_feedback_legacy_user_id_ddl;
EXECUTE v225_dataagent_help_feedback_legacy_user_id_stmt;
DEALLOCATE PREPARE v225_dataagent_help_feedback_legacy_user_id_stmt;

SET @v225_dataagent_help_feedback_user_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_feedback' AND COLUMN_NAME = 'user_id'
);
SET @v225_dataagent_help_feedback_user_id_ddl := IF(@v225_dataagent_help_feedback_user_id_exists = 0,
    'ALTER TABLE `dataagent_help_feedback` ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT ''用户 ID''',
    'SELECT 1');
PREPARE v225_dataagent_help_feedback_user_id_stmt FROM @v225_dataagent_help_feedback_user_id_ddl;
EXECUTE v225_dataagent_help_feedback_user_id_stmt;
DEALLOCATE PREPARE v225_dataagent_help_feedback_user_id_stmt;

UPDATE `dataagent_help_feedback`
SET `user_id` = CAST(`legacy_user_id` AS UNSIGNED)
WHERE `user_id` IS NULL AND `legacy_user_id` REGEXP '^[0-9]+$';

SET @v225_dataagent_datasource_idx_datasource_owner_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource' AND INDEX_NAME = 'idx_datasource_owner_id'
);
SET @v225_dataagent_datasource_idx_datasource_owner_id_ddl := IF(@v225_dataagent_datasource_idx_datasource_owner_id_exists = 0,
    'ALTER TABLE `dataagent_datasource` ADD KEY `idx_datasource_owner_id` (`owner_id`)',
    'SELECT 1');
PREPARE v225_dataagent_datasource_idx_datasource_owner_id_stmt FROM @v225_dataagent_datasource_idx_datasource_owner_id_ddl;
EXECUTE v225_dataagent_datasource_idx_datasource_owner_id_stmt;
DEALLOCATE PREPARE v225_dataagent_datasource_idx_datasource_owner_id_stmt;

SET @v225_dataagent_help_document_ft_help_doc_search_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_help_document' AND INDEX_NAME = 'ft_help_doc_search'
);
SET @v225_dataagent_help_document_ft_help_doc_search_ddl := IF(@v225_dataagent_help_document_ft_help_doc_search_exists = 0,
    'ALTER TABLE `dataagent_help_document` ADD FULLTEXT KEY `ft_help_doc_search` (`title`, `content`)',
    'SELECT 1');
PREPARE v225_dataagent_help_document_ft_help_doc_search_stmt FROM @v225_dataagent_help_document_ft_help_doc_search_ddl;
EXECUTE v225_dataagent_help_document_ft_help_doc_search_stmt;
DEALLOCATE PREPARE v225_dataagent_help_document_ft_help_doc_search_stmt;

SET @v225_dataagent_aloudata_metric_idx_metric_ds_category_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_metric' AND INDEX_NAME = 'idx_metric_ds_category'
);
SET @v225_dataagent_aloudata_metric_idx_metric_ds_category_ddl := IF(@v225_dataagent_aloudata_metric_idx_metric_ds_category_exists = 0,
    'ALTER TABLE `dataagent_aloudata_metric` ADD KEY `idx_metric_ds_category` (`datasource_id`, `metric_category_id`)',
    'SELECT 1');
PREPARE v225_dataagent_aloudata_metric_idx_metric_ds_category_stmt FROM @v225_dataagent_aloudata_metric_idx_metric_ds_category_ddl;
EXECUTE v225_dataagent_aloudata_metric_idx_metric_ds_category_stmt;
DEALLOCATE PREPARE v225_dataagent_aloudata_metric_idx_metric_ds_category_stmt;

SET @v225_dataagent_aloudata_metric_idx_metric_keyword_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_metric' AND INDEX_NAME = 'idx_metric_keyword'
);
SET @v225_dataagent_aloudata_metric_idx_metric_keyword_ddl := IF(@v225_dataagent_aloudata_metric_idx_metric_keyword_exists = 0,
    'ALTER TABLE `dataagent_aloudata_metric` ADD KEY `idx_metric_keyword` (`datasource_id`, `metric_name`, `metric_display_name`)',
    'SELECT 1');
PREPARE v225_dataagent_aloudata_metric_idx_metric_keyword_stmt FROM @v225_dataagent_aloudata_metric_idx_metric_keyword_ddl;
EXECUTE v225_dataagent_aloudata_metric_idx_metric_keyword_stmt;
DEALLOCATE PREPARE v225_dataagent_aloudata_metric_idx_metric_keyword_stmt;

SET @v225_dataagent_aloudata_dimension_idx_dim_ds_category_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_dimension' AND INDEX_NAME = 'idx_dim_ds_category'
);
SET @v225_dataagent_aloudata_dimension_idx_dim_ds_category_ddl := IF(@v225_dataagent_aloudata_dimension_idx_dim_ds_category_exists = 0,
    'ALTER TABLE `dataagent_aloudata_dimension` ADD KEY `idx_dim_ds_category` (`datasource_id`, `dim_category_id`)',
    'SELECT 1');
PREPARE v225_dataagent_aloudata_dimension_idx_dim_ds_category_stmt FROM @v225_dataagent_aloudata_dimension_idx_dim_ds_category_ddl;
EXECUTE v225_dataagent_aloudata_dimension_idx_dim_ds_category_stmt;
DEALLOCATE PREPARE v225_dataagent_aloudata_dimension_idx_dim_ds_category_stmt;

SET @v225_dataagent_aloudata_dimension_idx_dim_keyword_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_dimension' AND INDEX_NAME = 'idx_dim_keyword'
);
SET @v225_dataagent_aloudata_dimension_idx_dim_keyword_ddl := IF(@v225_dataagent_aloudata_dimension_idx_dim_keyword_exists = 0,
    'ALTER TABLE `dataagent_aloudata_dimension` ADD KEY `idx_dim_keyword` (`datasource_id`, `dim_name`, `dim_display_name`)',
    'SELECT 1');
PREPARE v225_dataagent_aloudata_dimension_idx_dim_keyword_stmt FROM @v225_dataagent_aloudata_dimension_idx_dim_keyword_ddl;
EXECUTE v225_dataagent_aloudata_dimension_idx_dim_keyword_stmt;
DEALLOCATE PREPARE v225_dataagent_aloudata_dimension_idx_dim_keyword_stmt;

SET @v225_dataagent_aloudata_category_idx_category_ds_type_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_category' AND INDEX_NAME = 'idx_category_ds_type_id'
);
SET @v225_dataagent_aloudata_category_idx_category_ds_type_id_ddl := IF(@v225_dataagent_aloudata_category_idx_category_ds_type_id_exists = 0,
    'ALTER TABLE `dataagent_aloudata_category` ADD KEY `idx_category_ds_type_id` (`datasource_id`, `category_type`, `category_id`, `parent_id`)',
    'SELECT 1');
PREPARE v225_dataagent_aloudata_category_idx_category_ds_type_id_stmt FROM @v225_dataagent_aloudata_category_idx_category_ds_type_id_ddl;
EXECUTE v225_dataagent_aloudata_category_idx_category_ds_type_id_stmt;
DEALLOCATE PREPARE v225_dataagent_aloudata_category_idx_category_ds_type_id_stmt;

-- Restore unique indexes omitted from already-deployed CREATE TABLE migrations.
-- Existing duplicate rows are preserved; MySQL will stop at the first conflicting index.
SET @v225_unique_metric_ds_name_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_metric' AND INDEX_NAME = 'uk_metric_ds_name'
);
SET @v225_unique_metric_ds_name_ddl := IF(@v225_unique_metric_ds_name_exists = 0,
    'ALTER TABLE `dataagent_aloudata_metric` ADD UNIQUE KEY `uk_metric_ds_name` (`datasource_id`, `metric_name`)', 'SELECT 1');
PREPARE v225_unique_metric_ds_name_stmt FROM @v225_unique_metric_ds_name_ddl;
EXECUTE v225_unique_metric_ds_name_stmt;
DEALLOCATE PREPARE v225_unique_metric_ds_name_stmt;

SET @v225_unique_dim_ds_name_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_dimension' AND INDEX_NAME = 'uk_dim_ds_name'
);
SET @v225_unique_dim_ds_name_ddl := IF(@v225_unique_dim_ds_name_exists = 0,
    'ALTER TABLE `dataagent_aloudata_dimension` ADD UNIQUE KEY `uk_dim_ds_name` (`datasource_id`, `dim_name`)', 'SELECT 1');
PREPARE v225_unique_dim_ds_name_stmt FROM @v225_unique_dim_ds_name_ddl;
EXECUTE v225_unique_dim_ds_name_stmt;
DEALLOCATE PREPARE v225_unique_dim_ds_name_stmt;

SET @v225_unique_metric_dim_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_metric_dimension' AND INDEX_NAME = 'uk_metric_dim'
);
SET @v225_unique_metric_dim_ddl := IF(@v225_unique_metric_dim_exists = 0,
    'ALTER TABLE `dataagent_aloudata_metric_dimension` ADD UNIQUE KEY `uk_metric_dim` (`datasource_id`, `metric_name`, `dim_name`)', 'SELECT 1');
PREPARE v225_unique_metric_dim_stmt FROM @v225_unique_metric_dim_ddl;
EXECUTE v225_unique_metric_dim_stmt;
DEALLOCATE PREPARE v225_unique_metric_dim_stmt;

SET @v225_unique_category_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_aloudata_category' AND INDEX_NAME = 'uk_category'
);
SET @v225_unique_category_ddl := IF(@v225_unique_category_exists = 0,
    'ALTER TABLE `dataagent_aloudata_category` ADD UNIQUE KEY `uk_category` (`datasource_id`, `category_id`)', 'SELECT 1');
PREPARE v225_unique_category_stmt FROM @v225_unique_category_ddl;
EXECUTE v225_unique_category_stmt;
DEALLOCATE PREPARE v225_unique_category_stmt;

SET @v225_unique_semantic_model_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_semantic_model' AND INDEX_NAME = 'uk_semantic_model'
);
SET @v225_unique_semantic_model_ddl := IF(@v225_unique_semantic_model_exists = 0,
    'ALTER TABLE `dataagent_semantic_model` ADD UNIQUE KEY `uk_semantic_model` (`datasource_id`, `table_name`, `column_name`, `deleted`)', 'SELECT 1');
PREPARE v225_unique_semantic_model_stmt FROM @v225_unique_semantic_model_ddl;
EXECUTE v225_unique_semantic_model_stmt;
DEALLOCATE PREPARE v225_unique_semantic_model_stmt;

SET @v225_unique_tenant_term_name_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_business_term' AND INDEX_NAME = 'uk_tenant_term_name'
);
SET @v225_unique_tenant_term_name_ddl := IF(@v225_unique_tenant_term_name_exists = 0,
    'ALTER TABLE `dataagent_business_term` ADD UNIQUE KEY `uk_tenant_term_name` (`tenant_code`, `term_name`, `deleted`)', 'SELECT 1');
PREPARE v225_unique_tenant_term_name_stmt FROM @v225_unique_tenant_term_name_ddl;
EXECUTE v225_unique_tenant_term_name_stmt;
DEALLOCATE PREPARE v225_unique_tenant_term_name_stmt;

SET @v225_unique_resource_grant_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_resource_grant' AND INDEX_NAME = 'uk_resource_grant'
);
SET @v225_unique_resource_grant_ddl := IF(@v225_unique_resource_grant_exists = 0,
    'ALTER TABLE `dataagent_resource_grant` ADD UNIQUE KEY `uk_resource_grant` (`resource_type`, `resource_id`, `grant_type`, `grantee_id`, `permission`, `deleted`)', 'SELECT 1');
PREPARE v225_unique_resource_grant_stmt FROM @v225_unique_resource_grant_ddl;
EXECUTE v225_unique_resource_grant_stmt;
DEALLOCATE PREPARE v225_unique_resource_grant_stmt;

SET @v225_unique_datasource_user_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_datasource_account' AND INDEX_NAME = 'uk_datasource_user'
);
SET @v225_unique_datasource_user_ddl := IF(@v225_unique_datasource_user_exists = 0,
    'ALTER TABLE `dataagent_datasource_account` ADD UNIQUE KEY `uk_datasource_user` (`datasource_id`, `user_id`, `deleted`)', 'SELECT 1');
PREPARE v225_unique_datasource_user_stmt FROM @v225_unique_datasource_user_ddl;
EXECUTE v225_unique_datasource_user_stmt;
DEALLOCATE PREPARE v225_unique_datasource_user_stmt;

SET @v225_unique_dashboard_execution_id_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dashboard_execution' AND INDEX_NAME = 'uk_dataagent_dashboard_execution_id'
);
SET @v225_unique_dashboard_execution_id_ddl := IF(@v225_unique_dashboard_execution_id_exists = 0,
    'ALTER TABLE `dataagent_dashboard_execution` ADD UNIQUE KEY `uk_dataagent_dashboard_execution_id` (`execution_id`)', 'SELECT 1');
PREPARE v225_unique_dashboard_execution_id_stmt FROM @v225_unique_dashboard_execution_id_ddl;
EXECUTE v225_unique_dashboard_execution_id_stmt;
DEALLOCATE PREPARE v225_unique_dashboard_execution_id_stmt;

SET @v225_unique_username_tenant_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_user_uid_mapping' AND INDEX_NAME = 'uk_username_tenant'
);
SET @v225_unique_username_tenant_ddl := IF(@v225_unique_username_tenant_exists = 0,
    'ALTER TABLE `dataagent_user_uid_mapping` ADD UNIQUE KEY `uk_username_tenant` (`username`, `tenant_id`)', 'SELECT 1');
PREPARE v225_unique_username_tenant_stmt FROM @v225_unique_username_tenant_ddl;
EXECUTE v225_unique_username_tenant_stmt;
DEALLOCATE PREPARE v225_unique_username_tenant_stmt;

-- Recover missing endpoint initialization without replacing administrator-defined values.
UPDATE `mate_system_setting`
SET `setting_value` = JSON_SET(
        `setting_value`,
        '$.dimension_values',
        CAST('{"service":"anymetrics","path":"/anymetrics/api/v1/dimension/values","method":"POST","description":"查询维度值，支持关键词和分页。","requestParams":[{"name":"tenant-id","type":"String","required":true,"paramLocation":"HEADER"},{"name":"auth-type","type":"String","required":true,"paramLocation":"HEADER"},{"name":"auth-value","type":"String","required":true,"paramLocation":"HEADER"},{"name":"dimName","type":"String","required":true,"paramLocation":"BODY"},{"name":"dimValueKeyword","type":"String","required":false,"paramLocation":"BODY"},{"name":"pageNumber","type":"Integer","required":false,"defaultValue":1,"paramLocation":"BODY"},{"name":"pageSize","type":"Integer","required":false,"defaultValue":200,"paramLocation":"BODY"}],"responseParams":[{"name":"success","type":"Boolean","required":true},{"name":"data.table","type":"Object","required":true},{"name":"data.table.<dimName>","type":"Array[Array]","required":true}]}' AS JSON)
    ),
    `update_time` = NOW()
WHERE `setting_key` = 'aloudata.api.endpoints'
  AND `setting_value` IS NOT NULL
  AND JSON_VALID(`setting_value`)
  AND JSON_CONTAINS_PATH(`setting_value`, 'one', '$.dimension_values') = 0;

-- V220's nullable FILE dataset contract, applied only when an old schema still has NOT NULL.
SET @v225_dataset_source_required := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_dataset'
      AND COLUMN_NAME = 'datasource_id' AND IS_NULLABLE = 'NO'
);
SET @v225_dataset_source_nullable_ddl := IF(@v225_dataset_source_required > 0,
    'ALTER TABLE `dataagent_dataset` MODIFY COLUMN `datasource_id` BIGINT DEFAULT NULL COMMENT ''关联数据源 ID''',
    'SELECT 1');
PREPARE v225_dataset_source_nullable_stmt FROM @v225_dataset_source_nullable_ddl;
EXECUTE v225_dataset_source_nullable_stmt;
DEALLOCATE PREPARE v225_dataset_source_nullable_stmt;
