-- ============================================================
-- Aloudata 语义层：指标元数据、维度元数据、指标-维度关联、类目
-- ============================================================
-- 替代 dataagent_semantic_model 对 Aloudata 数据源的表级存储，
-- 改为指标级 + 维度级粒度，支持 ES 混合检索。
-- 同步来源：Aloudata API（metrics_list, metric_batch_detail,
--           metric_all_dimensions, dimensions_list, dimension_detail,
--           category_list）
--
-- 设计说明：
--   1. 不加 workspace_id：这三张表通过 datasource_id 关联，datasource_id 已有 workspace_id，
--      无需冗余存储 workspace_id。
--   2. 不加 deleted 字段：全量同步采用 upsert + 逻辑替换策略（通过 sync_version 标识），
--      不依赖软删除字段。
-- ============================================================

-- 1. Aloudata 指标元数据表

CREATE TABLE IF NOT EXISTS dataagent_aloudata_metric (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    metric_code VARCHAR(64) DEFAULT NULL,
    metric_name VARCHAR(128) NOT NULL,
    metric_display_name VARCHAR(128) NOT NULL,
    version INTEGER DEFAULT '1',
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    publish_status VARCHAR(32) DEFAULT NULL,
    display_status VARCHAR(32) DEFAULT NULL,
    business_caliber TEXT DEFAULT NULL,
    owner VARCHAR(128) DEFAULT NULL,
    business_owner VARCHAR(128) DEFAULT NULL,
    metric_category_id VARCHAR(64) DEFAULT NULL,
    metric_category_name VARCHAR(128) DEFAULT NULL,
    unit VARCHAR(32) DEFAULT NULL,
    cn_unit VARCHAR(32) DEFAULT NULL,
    metric_view_count INTEGER DEFAULT '0',
    time_granularity VARCHAR(32) DEFAULT NULL,
    has_date_limit BOOLEAN DEFAULT FALSE,
    has_derivation_method BOOLEAN DEFAULT FALSE,
    metric_time_data_type VARCHAR(32) DEFAULT NULL,
    can_edit BOOLEAN DEFAULT TRUE,
    can_delete BOOLEAN DEFAULT FALSE,
    can_usage BOOLEAN DEFAULT TRUE,
    can_auth BOOLEAN DEFAULT FALSE,
    can_transfer BOOLEAN DEFAULT FALSE,
    properties TEXT DEFAULT NULL,
    gmt_create VARCHAR(20) DEFAULT NULL,
    gmt_update VARCHAR(20) DEFAULT NULL,
    synonyms TEXT DEFAULT NULL,
    embedding_text TEXT DEFAULT NULL,
    embedding BYTEA DEFAULT NULL,
    embedding_model_id BIGINT DEFAULT NULL,
    sync_version INTEGER DEFAULT '0',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_datasource_id ON dataagent_aloudata_metric (datasource_id);

CREATE INDEX IF NOT EXISTS idx_metric_name ON dataagent_aloudata_metric (metric_name);

CREATE INDEX IF NOT EXISTS idx_metric_category_id ON dataagent_aloudata_metric (metric_category_id);

CREATE INDEX IF NOT EXISTS idx_type ON dataagent_aloudata_metric (type);

CREATE INDEX IF NOT EXISTS idx_status ON dataagent_aloudata_metric (status);

CREATE INDEX IF NOT EXISTS idx_sync_version ON dataagent_aloudata_metric (sync_version);

CREATE INDEX IF NOT EXISTS idx_metric_ds_category ON dataagent_aloudata_metric (datasource_id, metric_category_id);

CREATE INDEX IF NOT EXISTS idx_metric_ds_name ON dataagent_aloudata_metric (datasource_id, metric_name);

CREATE INDEX IF NOT EXISTS idx_metric_keyword ON dataagent_aloudata_metric (datasource_id, metric_name, metric_display_name);

COMMENT ON TABLE dataagent_aloudata_metric IS 'Aloudata指标元数据表';

COMMENT ON COLUMN dataagent_aloudata_metric.id IS '主键ID';
COMMENT ON COLUMN dataagent_aloudata_metric.datasource_id IS '关联数据源ID';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_code IS '指标编码（系统内部唯一标识）';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_name IS '指标英文名';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_display_name IS '指标展示名（业务别名）';
COMMENT ON COLUMN dataagent_aloudata_metric.version IS '指标版本号';
COMMENT ON COLUMN dataagent_aloudata_metric.type IS '指标类型：ATOMIC/DERIVED/COMPOSITE';
COMMENT ON COLUMN dataagent_aloudata_metric.status IS '指标终态：ONLINE/OFFLINE';
COMMENT ON COLUMN dataagent_aloudata_metric.publish_status IS '发布状态：DRAFT/PUBLISHED';
COMMENT ON COLUMN dataagent_aloudata_metric.display_status IS '显示状态：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE';
COMMENT ON COLUMN dataagent_aloudata_metric.business_caliber IS '业务口径描述';
COMMENT ON COLUMN dataagent_aloudata_metric.owner IS '指标负责人';
COMMENT ON COLUMN dataagent_aloudata_metric.business_owner IS '业务负责人';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_category_id IS '指标类目ID';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_category_name IS '指标类目名称';
COMMENT ON COLUMN dataagent_aloudata_metric.unit IS '指标单位';
COMMENT ON COLUMN dataagent_aloudata_metric.cn_unit IS '中文指标单位';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_view_count IS '指标查询次数';
COMMENT ON COLUMN dataagent_aloudata_metric.time_granularity IS '时间粒度（数据统计的时间单位）';
COMMENT ON COLUMN dataagent_aloudata_metric.has_date_limit IS '是否有日期限制：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.has_derivation_method IS '是否有衍生方法：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.metric_time_data_type IS '指标时间数据类型：DATE_TIME';
COMMENT ON COLUMN dataagent_aloudata_metric.can_edit IS '是否允许编辑：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.can_delete IS '是否允许删除：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.can_usage IS '是否允许使用：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.can_auth IS '是否允许授权：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.can_transfer IS '是否允许转移：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_metric.properties IS '指标属性JSON（MANAGE/BUSINESS/TECHNOLOGY/BASE）';
COMMENT ON COLUMN dataagent_aloudata_metric.gmt_create IS '创建时间（Aloudata原始格式）';
COMMENT ON COLUMN dataagent_aloudata_metric.gmt_update IS '修改时间（Aloudata原始格式）';
COMMENT ON COLUMN dataagent_aloudata_metric.synonyms IS '同义词（逗号分隔）';
COMMENT ON COLUMN dataagent_aloudata_metric.embedding_text IS '嵌入文本（用于生成向量）';
COMMENT ON COLUMN dataagent_aloudata_metric.embedding IS '向量数据（float32小端序序列化）';
COMMENT ON COLUMN dataagent_aloudata_metric.embedding_model_id IS '嵌入模型ID';
COMMENT ON COLUMN dataagent_aloudata_metric.sync_version IS '同步版本号（每次全量同步递增）';
COMMENT ON COLUMN dataagent_aloudata_metric.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_aloudata_metric.update_time IS '更新时间';

DROP TRIGGER IF EXISTS trg_dataagent_aloudata_metric_upd_ts ON dataagent_aloudata_metric;
CREATE TRIGGER trg_dataagent_aloudata_metric_upd_ts BEFORE UPDATE ON dataagent_aloudata_metric
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS dataagent_aloudata_dimension (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    dim_name VARCHAR(128) NOT NULL,
    dim_code VARCHAR(64) DEFAULT NULL,
    dim_display_name VARCHAR(150) NOT NULL,
    dim_category_id VARCHAR(64) DEFAULT NULL,
    dim_category_name VARCHAR(128) DEFAULT NULL,
    dim_description TEXT DEFAULT NULL,
    dataset_name VARCHAR(128) DEFAULT NULL,
    origin_data_type VARCHAR(32) DEFAULT NULL,
    display_status VARCHAR(32) DEFAULT NULL,
    config_type VARCHAR(32) DEFAULT NULL,
    config_value TEXT DEFAULT NULL,
    is_time_dimension BOOLEAN DEFAULT FALSE,
    synonyms TEXT DEFAULT NULL,
    example_values TEXT DEFAULT NULL,
    embedding_text TEXT DEFAULT NULL,
    embedding BYTEA DEFAULT NULL,
    embedding_model_id BIGINT DEFAULT NULL,
    sync_version INTEGER DEFAULT '0',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dim_ds_name ON dataagent_aloudata_dimension (datasource_id, dim_name);

CREATE INDEX IF NOT EXISTS dataagent_aloudata_dimension_idx_datasource_id ON dataagent_aloudata_dimension (datasource_id);

CREATE INDEX IF NOT EXISTS idx_dim_name ON dataagent_aloudata_dimension (dim_name);

CREATE INDEX IF NOT EXISTS idx_dim_category_id ON dataagent_aloudata_dimension (dim_category_id);

CREATE INDEX IF NOT EXISTS idx_dataset_name ON dataagent_aloudata_dimension (dataset_name);

CREATE INDEX IF NOT EXISTS dataagent_aloudata_dimension_idx_sync_version ON dataagent_aloudata_dimension (sync_version);

CREATE INDEX IF NOT EXISTS idx_dim_ds_category ON dataagent_aloudata_dimension (datasource_id, dim_category_id);

CREATE INDEX IF NOT EXISTS idx_dim_keyword ON dataagent_aloudata_dimension (datasource_id, dim_name, dim_display_name);

COMMENT ON TABLE dataagent_aloudata_dimension IS 'Aloudata维度元数据表';

COMMENT ON COLUMN dataagent_aloudata_dimension.id IS '主键ID';
COMMENT ON COLUMN dataagent_aloudata_dimension.datasource_id IS '关联数据源ID';
COMMENT ON COLUMN dataagent_aloudata_dimension.dim_name IS '维度名称（租户下唯一）';
COMMENT ON COLUMN dataagent_aloudata_dimension.dim_code IS '维度编码';
COMMENT ON COLUMN dataagent_aloudata_dimension.dim_display_name IS '维度中文名';
COMMENT ON COLUMN dataagent_aloudata_dimension.dim_category_id IS '维度类目ID（未分类为-1）';
COMMENT ON COLUMN dataagent_aloudata_dimension.dim_category_name IS '维度类目名称';
COMMENT ON COLUMN dataagent_aloudata_dimension.dim_description IS '维度描述';
COMMENT ON COLUMN dataagent_aloudata_dimension.dataset_name IS '数据集名称';
COMMENT ON COLUMN dataagent_aloudata_dimension.origin_data_type IS '原始数据类型（如VARCHAR）';
COMMENT ON COLUMN dataagent_aloudata_dimension.display_status IS '维度状态：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE';
COMMENT ON COLUMN dataagent_aloudata_dimension.config_type IS '维度类型：COLUMN_BIND/CUSTOM';
COMMENT ON COLUMN dataagent_aloudata_dimension.config_value IS '列名或自定义表达式';
COMMENT ON COLUMN dataagent_aloudata_dimension.is_time_dimension IS '是否时间维度：0-否，1-是';
COMMENT ON COLUMN dataagent_aloudata_dimension.synonyms IS '同义词（逗号分隔）';
COMMENT ON COLUMN dataagent_aloudata_dimension.example_values IS '示例值（逗号分隔，低基数维度）';
COMMENT ON COLUMN dataagent_aloudata_dimension.embedding_text IS '嵌入文本（用于生成向量）';
COMMENT ON COLUMN dataagent_aloudata_dimension.embedding IS '向量数据（float32小端序序列化）';
COMMENT ON COLUMN dataagent_aloudata_dimension.embedding_model_id IS '嵌入模型ID';
COMMENT ON COLUMN dataagent_aloudata_dimension.sync_version IS '同步版本号（每次全量同步递增）';
COMMENT ON COLUMN dataagent_aloudata_dimension.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_aloudata_dimension.update_time IS '更新时间';

DROP TRIGGER IF EXISTS trg_dataagent_aloudata_dimension_upd_ts ON dataagent_aloudata_dimension;
CREATE TRIGGER trg_dataagent_aloudata_dimension_upd_ts BEFORE UPDATE ON dataagent_aloudata_dimension
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS dataagent_aloudata_metric_dimension (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    metric_name VARCHAR(200) NOT NULL,
    dim_name VARCHAR(200) NOT NULL,
    dim_display_name VARCHAR(200) DEFAULT NULL,
    origin_data_type VARCHAR(100) DEFAULT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_metric_dim ON dataagent_aloudata_metric_dimension (datasource_id, metric_name, dim_name);

CREATE INDEX IF NOT EXISTS idx_md_metric ON dataagent_aloudata_metric_dimension (datasource_id, metric_name);

CREATE INDEX IF NOT EXISTS idx_md_dim ON dataagent_aloudata_metric_dimension (datasource_id, dim_name);

COMMENT ON TABLE dataagent_aloudata_metric_dimension IS '指标-维度关联关系';

COMMENT ON COLUMN dataagent_aloudata_metric_dimension.id IS '主键 ID';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.metric_name IS '指标英文名';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.dim_name IS '维度英文名';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.dim_display_name IS '维度展示名';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.origin_data_type IS '维度数据类型';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.sync_version IS '同步版本号';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_aloudata_metric_dimension.update_time IS '更新时间';

DROP TRIGGER IF EXISTS trg_dataagent_aloudata_metric_dimension_upd_ts ON dataagent_aloudata_metric_dimension;
CREATE TRIGGER trg_dataagent_aloudata_metric_dimension_upd_ts BEFORE UPDATE ON dataagent_aloudata_metric_dimension
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS dataagent_aloudata_category (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    category_id VARCHAR(100) NOT NULL,
    category_name VARCHAR(200) DEFAULT NULL,
    category_type VARCHAR(50) DEFAULT NULL,
    parent_id VARCHAR(100) DEFAULT NULL,
    front_id VARCHAR(100) DEFAULT NULL,
    type VARCHAR(50) DEFAULT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_category ON dataagent_aloudata_category (datasource_id, category_id);

CREATE INDEX IF NOT EXISTS idx_category_datasource_id ON dataagent_aloudata_category (datasource_id);

CREATE INDEX IF NOT EXISTS idx_category_type ON dataagent_aloudata_category (datasource_id, category_type);

CREATE INDEX IF NOT EXISTS idx_category_ds_type_id ON dataagent_aloudata_category (datasource_id, category_type, category_id, parent_id);

COMMENT ON TABLE dataagent_aloudata_category IS 'Aloudata 类目元数据';

COMMENT ON COLUMN dataagent_aloudata_category.id IS '主键 ID';
COMMENT ON COLUMN dataagent_aloudata_category.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_aloudata_category.category_id IS '类目 ID（Aloudata 平台标识）';
COMMENT ON COLUMN dataagent_aloudata_category.category_name IS '类目名称';
COMMENT ON COLUMN dataagent_aloudata_category.category_type IS '类目类型：CATEGORY_METRIC/CATEGORY_DIMENSION/CATEGORY_DATASET';
COMMENT ON COLUMN dataagent_aloudata_category.parent_id IS '父级类目 ID';
COMMENT ON COLUMN dataagent_aloudata_category.front_id IS '上级类目 ID（frontId）';
COMMENT ON COLUMN dataagent_aloudata_category.type IS '类型：SYSTEM（系统类目）/ null（用户自定义）';
COMMENT ON COLUMN dataagent_aloudata_category.sync_version IS '同步版本号';
COMMENT ON COLUMN dataagent_aloudata_category.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_aloudata_category.update_time IS '更新时间';

DROP TRIGGER IF EXISTS trg_dataagent_aloudata_category_upd_ts ON dataagent_aloudata_category;
CREATE TRIGGER trg_dataagent_aloudata_category_upd_ts BEFORE UPDATE ON dataagent_aloudata_category
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
