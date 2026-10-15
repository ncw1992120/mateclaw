-- V225: 收敛历史版本的 DataAgent 表结构。

-- 该迁移只追加，不修改 V200-V224；所有建表语句均使用 IF NOT EXISTS。

-- 先补齐缺失表和列，再补充非唯一索引。业务唯一索引由单独步骤处理。



CREATE TABLE IF NOT EXISTS dataagent_datasource (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    owner_id BIGINT DEFAULT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    source_type VARCHAR(50) NOT NULL,
    host VARCHAR(255) DEFAULT NULL,
    product_host VARCHAR(255) DEFAULT NULL,
    semantic_host VARCHAR(255) DEFAULT NULL,
    port INTEGER DEFAULT NULL,
    database_name VARCHAR(255) DEFAULT NULL,
    username VARCHAR(200) DEFAULT NULL,
    password VARCHAR(500) DEFAULT NULL,
    connection_params TEXT DEFAULT NULL,
    schema_name VARCHAR(200) DEFAULT NULL,
    meta_shared BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_test_time TIMESTAMP DEFAULT NULL,
    last_test_ok BOOLEAN DEFAULT NULL,
    schema_status VARCHAR(20) DEFAULT 'pending',
    last_schema_discovery_time TIMESTAMP DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_datasource_tables (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    table_comment VARCHAR(500) DEFAULT NULL,
    table_type VARCHAR(30) DEFAULT 'table',
    row_count BIGINT DEFAULT NULL,
    data_size_bytes BIGINT DEFAULT NULL,
    schema_name VARCHAR(200) DEFAULT NULL,
    engine VARCHAR(100) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_datasource_columns (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    column_comment VARCHAR(500) DEFAULT NULL,
    data_type VARCHAR(100) NOT NULL,
    column_size INTEGER DEFAULT NULL,
    decimal_digits INTEGER DEFAULT NULL,
    nullable BOOLEAN DEFAULT TRUE,
    primary_key BOOLEAN DEFAULT FALSE,
    indexed BOOLEAN DEFAULT FALSE,
    default_value VARCHAR(500) DEFAULT NULL,
    ordinal_position INTEGER DEFAULT NULL,
    foreign_key_table VARCHAR(255) DEFAULT NULL,
    foreign_key_column VARCHAR(255) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_dataset (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    datasource_id BIGINT NOT NULL,
    datasource_name VARCHAR(200) DEFAULT NULL,
    table_ids TEXT DEFAULT NULL,
    table_names TEXT DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    row_count BIGINT DEFAULT 0,
    column_count INTEGER DEFAULT 0,
    owner_id BIGINT DEFAULT NULL,
    modifier VARCHAR(100) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_dataset_field (
    id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    column_alias VARCHAR(255) DEFAULT NULL,
    column_comment VARCHAR(500) DEFAULT NULL,
    data_type VARCHAR(100) NOT NULL,
    column_size INTEGER DEFAULT NULL,
    decimal_digits INTEGER DEFAULT NULL,
    field_category VARCHAR(20) DEFAULT 'dimension',
    primary_key BOOLEAN DEFAULT FALSE,
    nullable BOOLEAN DEFAULT TRUE,
    default_value VARCHAR(500) DEFAULT NULL,
    ordinal_position INTEGER DEFAULT NULL,
    datasource_id BIGINT DEFAULT NULL,
    source_table_id BIGINT DEFAULT NULL,
    source_table_name VARCHAR(255) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_dataset_data (
    id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    row_data JSONB NOT NULL,
    row_hash VARCHAR(64) DEFAULT NULL,
    source_row_number INTEGER DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_semantic_model (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    datasource_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    business_name VARCHAR(200) DEFAULT NULL,
    business_description VARCHAR(500) DEFAULT NULL,
    synonyms VARCHAR(500) DEFAULT NULL,
    data_type VARCHAR(100) DEFAULT NULL,
    column_comment VARCHAR(500) DEFAULT NULL,
    example_values VARCHAR(500) DEFAULT NULL,
    enum_values TEXT DEFAULT NULL,
    unit VARCHAR(50) DEFAULT NULL,
    value_range VARCHAR(200) DEFAULT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_logical_relation (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    datasource_id BIGINT NOT NULL,
    source_table_name VARCHAR(128) NOT NULL,
    source_column_name VARCHAR(128) NOT NULL,
    target_table_name VARCHAR(128) NOT NULL,
    target_column_name VARCHAR(128) NOT NULL,
    relation_type VARCHAR(10) DEFAULT '1:N',
    description VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_schema_embedding (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    embedding_text TEXT NOT NULL,
    embedding BYTEA DEFAULT NULL,
    embedding_model_id BIGINT DEFAULT NULL,
    embedding_text_version INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

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

CREATE TABLE IF NOT EXISTS dataagent_help_category (
    id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    icon VARCHAR(100) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_help_document (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT DEFAULT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    sort_order INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    author VARCHAR(100) DEFAULT NULL,
    tags VARCHAR(500) DEFAULT NULL,
    view_count INTEGER DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_help_feedback (
    id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    rating INTEGER DEFAULT NULL,
    suggestion VARCHAR(1000) DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_business_term (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    tenant_code VARCHAR(64) NOT NULL,
    term_name VARCHAR(128) NOT NULL,
    synonyms VARCHAR(500) DEFAULT NULL,
    description TEXT DEFAULT NULL,
    calculation_formula TEXT DEFAULT NULL,
    data_caliber TEXT DEFAULT NULL,
    data_source VARCHAR(256) DEFAULT NULL,
    owner VARCHAR(128) DEFAULT NULL,
    business_rule TEXT DEFAULT NULL,
    related_terms VARCHAR(500) DEFAULT NULL,
    related_metrics_json TEXT,
    related_dimensions_json TEXT,
    example TEXT DEFAULT NULL,
    security_level VARCHAR(32) DEFAULT NULL,
    category VARCHAR(64) DEFAULT NULL,
    parent_id BIGINT DEFAULT NULL,
    embedding_text TEXT DEFAULT NULL,
    embedding BYTEA DEFAULT NULL,
    embedding_model_id BIGINT DEFAULT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_resource_grant (
    id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    grant_type VARCHAR(32) NOT NULL,
    grantee_id VARCHAR(128) NOT NULL,
    permission VARCHAR(32) NOT NULL DEFAULT 'use',
    granted_by BIGINT,
    status SMALLINT NOT NULL DEFAULT 1,
    expire_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_approval_record (
    id BIGINT NOT NULL,
    approval_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_name VARCHAR(255),
    workspace_id BIGINT NOT NULL DEFAULT 1,
    requester_id BIGINT NOT NULL,
    requester_name VARCHAR(128),
    action VARCHAR(32) NOT NULL,
    payload_json TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    current_step INTEGER NOT NULL DEFAULT 0,
    approver_id BIGINT,
    approver_name VARCHAR(128),
    comment TEXT,
    submitted_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_datasource_account (
    id BIGINT NOT NULL,
    datasource_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    query_username VARCHAR(200) NOT NULL,
    query_password VARCHAR(500) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    last_test_time TIMESTAMP DEFAULT NULL,
    last_test_ok BOOLEAN DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_insight_dashboard (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    schema_json TEXT NOT NULL,
    report_content TEXT DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    agent_id BIGINT DEFAULT NULL,
    owner_id BIGINT DEFAULT NULL,
    owner_name VARCHAR(100) DEFAULT NULL,
    modifier VARCHAR(100) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_insight_report (
    id BIGINT NOT NULL,
    dashboard_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT NULL,
    report_content TEXT DEFAULT NULL,
    echarts_options TEXT DEFAULT NULL,
    status VARCHAR(20) DEFAULT 'draft',
    owner_id BIGINT DEFAULT NULL,
    owner_name VARCHAR(100) DEFAULT NULL,
    modifier VARCHAR(100) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_insight_report_subscription (
    id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_query_state (
    id BIGINT NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    datasource_id BIGINT NOT NULL,
    metrics VARCHAR(4000) DEFAULT NULL,
    dimensions VARCHAR(2000) DEFAULT NULL,
    time_constraint VARCHAR(2000) DEFAULT NULL,
    filters VARCHAR(4000) DEFAULT NULL,
    orders VARCHAR(2000) DEFAULT NULL,
    metric_display_map TEXT,
    request_json TEXT,
    query_count INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_enterprise_account (
    id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    principal_name VARCHAR(128) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'PILOT',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataagent_dashboard_execution (
    id BIGINT NOT NULL,
    execution_id VARCHAR(128) NOT NULL,
    dashboard_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    parameters_json TEXT,
    output_json TEXT,
    output_ref_json TEXT,
    logs TEXT,
    error_message TEXT,
    return_code INTEGER,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dataagent_dashboard_execution PRIMARY KEY (id),
    CONSTRAINT uk_dataagent_dashboard_execution_id UNIQUE (execution_id)
);

CREATE TABLE IF NOT EXISTS dataagent_user_uid_mapping (
    id BIGINT NOT NULL,
    username VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    nickname VARCHAR(128) DEFAULT NULL,
    aloudata_uid VARCHAR(500) NOT NULL,
    status INTEGER NOT NULL DEFAULT 1,
    sync_source VARCHAR(32) DEFAULT NULL,
    sync_time TIMESTAMP DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- Restore columns that were added by editing already-deployed CREATE TABLE migrations.
-- Workspace rows with NULL scope retain the historic default workspace (id=1).

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS product_host VARCHAR(255);

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS semantic_host VARCHAR(255);

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS owner_id BIGINT DEFAULT NULL;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS meta_shared BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS aloudata_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS aloudata_sync_cron VARCHAR(100) DEFAULT NULL;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS last_aloudata_sync_time TIMESTAMP DEFAULT NULL;

ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS owner_id BIGINT DEFAULT NULL;

ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS source_type VARCHAR(40) NOT NULL DEFAULT 'JDBC_TABLE';

ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS source_config TEXT DEFAULT NULL;

ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS schema_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE dataagent_semantic_model ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE dataagent_semantic_model ADD COLUMN IF NOT EXISTS owner_id BIGINT DEFAULT NULL;

ALTER TABLE dataagent_logical_relation ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE dataagent_logical_relation ADD COLUMN IF NOT EXISTS owner_id BIGINT DEFAULT NULL;

ALTER TABLE dataagent_help_document ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE dataagent_help_document ADD COLUMN IF NOT EXISTS summary VARCHAR(500) DEFAULT NULL;

ALTER TABLE dataagent_help_document ADD COLUMN IF NOT EXISTS tags VARCHAR(500) DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS calculation_formula TEXT DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS data_caliber TEXT DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS data_source VARCHAR(256) DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS owner VARCHAR(128) DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS business_rule TEXT DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS related_terms VARCHAR(500) DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS related_metrics_json TEXT DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS related_dimensions_json TEXT DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS example TEXT DEFAULT NULL;

ALTER TABLE dataagent_business_term ADD COLUMN IF NOT EXISTS security_level VARCHAR(32) DEFAULT NULL;

ALTER TABLE dataagent_insight_dashboard ADD COLUMN IF NOT EXISTS report_content TEXT DEFAULT NULL;

UPDATE dataagent_datasource SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE dataagent_dataset SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE dataagent_semantic_model SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE dataagent_logical_relation SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE dataagent_help_document SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE dataagent_business_term SET workspace_id = 1 WHERE workspace_id IS NULL;

ALTER TABLE dataagent_datasource ALTER COLUMN workspace_id SET DEFAULT 1;
ALTER TABLE dataagent_datasource ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE dataagent_dataset ALTER COLUMN workspace_id SET DEFAULT 1;
ALTER TABLE dataagent_dataset ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE dataagent_semantic_model ALTER COLUMN workspace_id SET DEFAULT 1;
ALTER TABLE dataagent_semantic_model ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE dataagent_logical_relation ALTER COLUMN workspace_id SET DEFAULT 1;
ALTER TABLE dataagent_logical_relation ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE dataagent_help_document ALTER COLUMN workspace_id SET DEFAULT 1;
ALTER TABLE dataagent_help_document ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE dataagent_business_term ALTER COLUMN workspace_id SET DEFAULT 1;
ALTER TABLE dataagent_business_term ALTER COLUMN workspace_id SET NOT NULL;

-- Preserve textual feedback IDs before adopting the numeric user_id contract.
ALTER TABLE dataagent_help_feedback ADD COLUMN IF NOT EXISTS legacy_user_id VARCHAR(100);
DO $$
DECLARE user_id_type TEXT;
BEGIN
    SELECT data_type INTO user_id_type
    FROM information_schema.columns
    WHERE table_schema = current_schema() AND table_name = 'dataagent_help_feedback' AND column_name = 'user_id';
    IF user_id_type IN ('character varying', 'character', 'text') THEN
        EXECUTE 'UPDATE dataagent_help_feedback SET legacy_user_id = COALESCE(legacy_user_id, user_id::text) WHERE user_id IS NOT NULL';
        ALTER TABLE dataagent_help_feedback DROP COLUMN user_id;
    END IF;
END $$;
ALTER TABLE dataagent_help_feedback ADD COLUMN IF NOT EXISTS user_id BIGINT;
UPDATE dataagent_help_feedback
SET user_id = legacy_user_id::BIGINT
WHERE user_id IS NULL AND legacy_user_id ~ '^[0-9]+$';

-- V220's nullable FILE dataset contract.
ALTER TABLE dataagent_dataset ALTER COLUMN datasource_id DROP NOT NULL;

COMMENT ON COLUMN dataagent_datasource.product_host IS '产品层服务地址（Aloudata anymetrics）';
COMMENT ON COLUMN dataagent_datasource.semantic_host IS '语义层服务地址（Aloudata semantic）';
COMMENT ON COLUMN dataagent_datasource.owner_id IS '数据源创建者用户 ID';
COMMENT ON COLUMN dataagent_datasource.meta_shared IS '元数据是否共享';
COMMENT ON COLUMN dataagent_dataset.owner_id IS '所有者用户 ID';
COMMENT ON COLUMN dataagent_help_document.summary IS '文档摘要';
COMMENT ON COLUMN dataagent_help_document.tags IS '标签（逗号分隔）';
COMMENT ON COLUMN dataagent_help_feedback.legacy_user_id IS '历史用户标识（原字符串）';
COMMENT ON COLUMN dataagent_help_feedback.user_id IS '用户 ID';
COMMENT ON COLUMN dataagent_insight_dashboard.report_content IS 'AI 分析报告内容（HTML 格式）';

-- Recover the dimension-values endpoint for databases that marked the earlier
-- migration complete before this endpoint was added. Preserve custom entries.
UPDATE mate_system_setting
SET setting_value = (setting_value::jsonb || '{"dimension_values":{"service":"anymetrics","path":"/anymetrics/api/v1/dimension/values","method":"POST","description":"查询维度值，支持关键词和分页。","requestParams":[{"name":"tenant-id","type":"String","required":true,"paramLocation":"HEADER"},{"name":"auth-type","type":"String","required":true,"paramLocation":"HEADER"},{"name":"auth-value","type":"String","required":true,"paramLocation":"HEADER"},{"name":"dimName","type":"String","required":true,"paramLocation":"BODY"},{"name":"dimValueKeyword","type":"String","required":false,"paramLocation":"BODY"},{"name":"pageNumber","type":"Integer","required":false,"defaultValue":1,"paramLocation":"BODY"},{"name":"pageSize","type":"Integer","required":false,"defaultValue":200,"paramLocation":"BODY"}],"responseParams":[{"name":"success","type":"Boolean","required":true},{"name":"data.table","type":"Object","required":true},{"name":"data.table.<dimName>","type":"Array[Array]","required":true}]}}'::jsonb)::text,
    update_time = NOW()
WHERE setting_key = 'aloudata.api.endpoints'
  AND setting_value IS NOT NULL
  AND btrim(setting_value) <> ''
  AND jsonb_typeof(setting_value::jsonb) = 'object'
  AND NOT (setting_value::jsonb ? 'dimension_values');

-- Recreate missing PostgreSQL indexes on existing tables.
CREATE INDEX IF NOT EXISTS idx_datasource_workspace_id ON dataagent_datasource (workspace_id);

CREATE INDEX IF NOT EXISTS idx_datasource_owner_id ON dataagent_datasource (owner_id);

CREATE INDEX IF NOT EXISTS idx_datasource_source_type ON dataagent_datasource (source_type);

CREATE INDEX IF NOT EXISTS idx_datasource_enabled ON dataagent_datasource (enabled);

CREATE INDEX IF NOT EXISTS idx_table_datasource_id ON dataagent_datasource_tables (datasource_id);

CREATE INDEX IF NOT EXISTS idx_table_name ON dataagent_datasource_tables (datasource_id, table_name);

CREATE INDEX IF NOT EXISTS idx_column_datasource_id ON dataagent_datasource_columns (datasource_id);

CREATE INDEX IF NOT EXISTS idx_column_table_id ON dataagent_datasource_columns (table_id);

CREATE INDEX IF NOT EXISTS idx_column_name ON dataagent_datasource_columns (table_id, column_name);

CREATE INDEX IF NOT EXISTS idx_dataset_workspace_id ON dataagent_dataset (workspace_id);

CREATE INDEX IF NOT EXISTS idx_dataset_datasource_id ON dataagent_dataset (datasource_id);

CREATE INDEX IF NOT EXISTS idx_dataset_status ON dataagent_dataset (status);

CREATE INDEX IF NOT EXISTS idx_field_dataset_id ON dataagent_dataset_field (dataset_id);

CREATE INDEX IF NOT EXISTS idx_field_category ON dataagent_dataset_field (dataset_id, field_category);

CREATE INDEX IF NOT EXISTS idx_data_dataset_id ON dataagent_dataset_data (dataset_id);

CREATE INDEX IF NOT EXISTS idx_data_row_hash ON dataagent_dataset_data (dataset_id, row_hash);

CREATE UNIQUE INDEX IF NOT EXISTS uk_semantic_model ON dataagent_semantic_model (datasource_id, table_name, column_name, deleted);

CREATE INDEX IF NOT EXISTS idx_semantic_workspace_id ON dataagent_semantic_model (workspace_id);

CREATE INDEX IF NOT EXISTS idx_semantic_datasource_id ON dataagent_semantic_model (datasource_id);

CREATE INDEX IF NOT EXISTS idx_semantic_table_name ON dataagent_semantic_model (datasource_id, table_name);

CREATE INDEX IF NOT EXISTS idx_relation_workspace_id ON dataagent_logical_relation (workspace_id);

CREATE INDEX IF NOT EXISTS idx_relation_datasource_id ON dataagent_logical_relation (datasource_id);

CREATE INDEX IF NOT EXISTS idx_relation_source_table ON dataagent_logical_relation (datasource_id, source_table_name);

CREATE INDEX IF NOT EXISTS idx_relation_target_table ON dataagent_logical_relation (datasource_id, target_table_name);

CREATE INDEX IF NOT EXISTS idx_embedding_datasource_id ON dataagent_schema_embedding (datasource_id);

CREATE INDEX IF NOT EXISTS idx_embedding_table_name ON dataagent_schema_embedding (datasource_id, table_name);

CREATE INDEX IF NOT EXISTS idx_datasource_id ON dataagent_aloudata_metric (datasource_id);

CREATE INDEX IF NOT EXISTS idx_metric_name ON dataagent_aloudata_metric (metric_name);

CREATE INDEX IF NOT EXISTS idx_metric_category_id ON dataagent_aloudata_metric (metric_category_id);

CREATE INDEX IF NOT EXISTS idx_type ON dataagent_aloudata_metric (type);

CREATE INDEX IF NOT EXISTS idx_status ON dataagent_aloudata_metric (status);

CREATE INDEX IF NOT EXISTS idx_sync_version ON dataagent_aloudata_metric (sync_version);

CREATE INDEX IF NOT EXISTS idx_metric_ds_category ON dataagent_aloudata_metric (datasource_id, metric_category_id);

CREATE INDEX IF NOT EXISTS idx_metric_keyword ON dataagent_aloudata_metric (datasource_id, metric_name, metric_display_name);

CREATE INDEX IF NOT EXISTS dataagent_aloudata_dimension_idx_datasource_id ON dataagent_aloudata_dimension (datasource_id);

CREATE INDEX IF NOT EXISTS idx_dim_name ON dataagent_aloudata_dimension (dim_name);

CREATE INDEX IF NOT EXISTS idx_dim_category_id ON dataagent_aloudata_dimension (dim_category_id);

CREATE INDEX IF NOT EXISTS idx_dataset_name ON dataagent_aloudata_dimension (dataset_name);

CREATE INDEX IF NOT EXISTS dataagent_aloudata_dimension_idx_sync_version ON dataagent_aloudata_dimension (sync_version);

CREATE INDEX IF NOT EXISTS idx_dim_ds_category ON dataagent_aloudata_dimension (datasource_id, dim_category_id);

CREATE INDEX IF NOT EXISTS idx_dim_keyword ON dataagent_aloudata_dimension (datasource_id, dim_name, dim_display_name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_metric_dim ON dataagent_aloudata_metric_dimension (datasource_id, metric_name, dim_name);

CREATE INDEX IF NOT EXISTS idx_md_metric ON dataagent_aloudata_metric_dimension (datasource_id, metric_name);

CREATE INDEX IF NOT EXISTS idx_md_dim ON dataagent_aloudata_metric_dimension (datasource_id, dim_name);

CREATE UNIQUE INDEX IF NOT EXISTS uk_category ON dataagent_aloudata_category (datasource_id, category_id);

CREATE INDEX IF NOT EXISTS idx_category_datasource_id ON dataagent_aloudata_category (datasource_id);

CREATE INDEX IF NOT EXISTS idx_category_type ON dataagent_aloudata_category (datasource_id, category_type);

CREATE INDEX IF NOT EXISTS idx_category_ds_type_id ON dataagent_aloudata_category (datasource_id, category_type, category_id, parent_id);

CREATE INDEX IF NOT EXISTS idx_help_category_parent ON dataagent_help_category (parent_id);

CREATE INDEX IF NOT EXISTS idx_help_doc_workspace_id ON dataagent_help_document (workspace_id);

CREATE INDEX IF NOT EXISTS idx_help_doc_category ON dataagent_help_document (category_id);

CREATE INDEX IF NOT EXISTS idx_help_doc_status ON dataagent_help_document (status);

CREATE INDEX IF NOT EXISTS idx_help_feedback_document ON dataagent_help_feedback (document_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_term_name ON dataagent_business_term (tenant_code, term_name, deleted);

CREATE INDEX IF NOT EXISTS idx_term_workspace_id ON dataagent_business_term (workspace_id);

CREATE INDEX IF NOT EXISTS idx_tenant_code ON dataagent_business_term (tenant_code);

CREATE INDEX IF NOT EXISTS idx_category ON dataagent_business_term (tenant_code, category);

CREATE INDEX IF NOT EXISTS idx_parent_id ON dataagent_business_term (parent_id);

CREATE INDEX IF NOT EXISTS dataagent_business_term_idx_status ON dataagent_business_term (status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_grant ON dataagent_resource_grant (resource_type, resource_id, grant_type, grantee_id, permission, deleted);

CREATE INDEX IF NOT EXISTS idx_workspace_resource ON dataagent_resource_grant (workspace_id, resource_type, resource_id);

CREATE INDEX IF NOT EXISTS idx_grantee ON dataagent_resource_grant (grant_type, grantee_id, status);

CREATE INDEX IF NOT EXISTS idx_workspace_status ON dataagent_approval_record (workspace_id, status, deleted);

CREATE INDEX IF NOT EXISTS idx_requester ON dataagent_approval_record (requester_id, status, deleted);

CREATE INDEX IF NOT EXISTS idx_resource ON dataagent_approval_record (resource_type, resource_id, deleted);

CREATE UNIQUE INDEX IF NOT EXISTS uk_datasource_user ON dataagent_datasource_account (datasource_id, user_id, deleted);

CREATE INDEX IF NOT EXISTS idx_account_workspace_id ON dataagent_datasource_account (workspace_id);

CREATE INDEX IF NOT EXISTS idx_account_user_id ON dataagent_datasource_account (user_id);

CREATE INDEX IF NOT EXISTS idx_insight_workspace_id ON dataagent_insight_dashboard (workspace_id);

CREATE INDEX IF NOT EXISTS idx_insight_status ON dataagent_insight_dashboard (status);

CREATE INDEX IF NOT EXISTS idx_report_workspace_id ON dataagent_insight_report (workspace_id);

CREATE INDEX IF NOT EXISTS idx_report_dashboard_id ON dataagent_insight_report (dashboard_id);

CREATE INDEX IF NOT EXISTS idx_report_status ON dataagent_insight_report (status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_user ON dataagent_insight_report_subscription (report_id, user_id);

CREATE INDEX IF NOT EXISTS idx_subscription_workspace_id ON dataagent_insight_report_subscription (workspace_id);

CREATE INDEX IF NOT EXISTS idx_subscription_user_id ON dataagent_insight_report_subscription (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dataagent_query_state_conv_ds ON dataagent_query_state (conversation_id, datasource_id);

CREATE INDEX IF NOT EXISTS idx_dataagent_query_state_conversation ON dataagent_query_state (conversation_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_enterprise_username ON dataagent_enterprise_account (username);

CREATE INDEX IF NOT EXISTS idx_enterprise_principal ON dataagent_enterprise_account (principal_name);

CREATE INDEX IF NOT EXISTS idx_dataagent_dashboard_execution_dashboard ON dataagent_dashboard_execution (dashboard_id, create_time);

CREATE INDEX IF NOT EXISTS idx_dataagent_dashboard_execution_workspace ON dataagent_dashboard_execution (workspace_id, create_time);

CREATE UNIQUE INDEX IF NOT EXISTS uk_username_tenant ON dataagent_user_uid_mapping (username, tenant_id);

CREATE INDEX IF NOT EXISTS idx_uid_mapping_tenant ON dataagent_user_uid_mapping (tenant_id);
