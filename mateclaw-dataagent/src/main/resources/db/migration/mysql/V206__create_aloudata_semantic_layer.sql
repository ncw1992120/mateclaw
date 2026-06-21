-- ============================================================
-- Aloudata 语义层：指标元数据、维度元数据、指标-维度关联、类目
-- ============================================================
-- 替代 dataagent_semantic_model 对 Aloudata 数据源的表级存储，
-- 改为指标级 + 维度级粒度，支持 ES 混合检索。
-- 同步来源：Aloudata API（metrics_list, metric_batch_detail,
--           metric_all_dimensions, dimensions_list, dimension_detail,
--           category_list）

-- 1. Aloudata 指标元数据表
CREATE TABLE `dataagent_aloudata_metric` (
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
    KEY `idx_sync_version` (`sync_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Aloudata指标元数据表';

-- 2. Aloudata 维度元数据表
CREATE TABLE `dataagent_aloudata_dimension` (
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
    KEY `idx_datasource_id` (`datasource_id`),
    KEY `idx_dim_name` (`dim_name`),
    KEY `idx_dim_category_id` (`dim_category_id`),
    KEY `idx_dataset_name` (`dataset_name`),
    KEY `idx_sync_version` (`sync_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Aloudata维度元数据表';


-- 3. 指标-维度关联关系表
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

-- 4. Aloudata 类目元数据表
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
    KEY `idx_category_type` (`datasource_id`, `category_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Aloudata 类目元数据';
