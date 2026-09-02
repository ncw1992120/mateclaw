-- ============================================================
-- DataAgent 数据集功能：数据集主表 + 数据集字段表 + 数据集业务数据表
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

-- 1. 数据集主表

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

CREATE INDEX IF NOT EXISTS idx_dataset_workspace_id ON dataagent_dataset (workspace_id);

CREATE INDEX IF NOT EXISTS idx_dataset_datasource_id ON dataagent_dataset (datasource_id);

CREATE INDEX IF NOT EXISTS idx_dataset_status ON dataagent_dataset (status);

COMMENT ON TABLE dataagent_dataset IS '数据集主表';

COMMENT ON COLUMN dataagent_dataset.id IS '主键 ID';
COMMENT ON COLUMN dataagent_dataset.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_dataset.name IS '数据集名称';
COMMENT ON COLUMN dataagent_dataset.description IS '描述';
COMMENT ON COLUMN dataagent_dataset.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_dataset.datasource_name IS '数据源名称（冗余存储）';
COMMENT ON COLUMN dataagent_dataset.table_ids IS '关联的数据源表 ID（逗号分隔）';
COMMENT ON COLUMN dataagent_dataset.table_names IS '关联的数据源表名（逗号分隔）';
COMMENT ON COLUMN dataagent_dataset.status IS '数据集状态：draft/ready/error';
COMMENT ON COLUMN dataagent_dataset.row_count IS '行数';
COMMENT ON COLUMN dataagent_dataset.column_count IS '列数';
COMMENT ON COLUMN dataagent_dataset.owner_id IS '所有者用户 ID';
COMMENT ON COLUMN dataagent_dataset.modifier IS '修改人';
COMMENT ON COLUMN dataagent_dataset.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_dataset.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_dataset.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_dataset_upd_ts ON dataagent_dataset;
CREATE TRIGGER trg_dataagent_dataset_upd_ts BEFORE UPDATE ON dataagent_dataset
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

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

CREATE INDEX IF NOT EXISTS idx_field_dataset_id ON dataagent_dataset_field (dataset_id);

CREATE INDEX IF NOT EXISTS idx_field_category ON dataagent_dataset_field (dataset_id, field_category);

COMMENT ON TABLE dataagent_dataset_field IS '数据集字段表';

COMMENT ON COLUMN dataagent_dataset_field.id IS '主键 ID';
COMMENT ON COLUMN dataagent_dataset_field.dataset_id IS '所属数据集 ID';
COMMENT ON COLUMN dataagent_dataset_field.column_name IS '原始列名';
COMMENT ON COLUMN dataagent_dataset_field.column_alias IS '字段别名';
COMMENT ON COLUMN dataagent_dataset_field.column_comment IS '字段注释';
COMMENT ON COLUMN dataagent_dataset_field.data_type IS '数据类型';
COMMENT ON COLUMN dataagent_dataset_field.column_size IS '字段大小';
COMMENT ON COLUMN dataagent_dataset_field.decimal_digits IS '小数位数';
COMMENT ON COLUMN dataagent_dataset_field.field_category IS '字段分类：dimension/measure';
COMMENT ON COLUMN dataagent_dataset_field.primary_key IS '是否主键';
COMMENT ON COLUMN dataagent_dataset_field.nullable IS '是否可空';
COMMENT ON COLUMN dataagent_dataset_field.default_value IS '默认值';
COMMENT ON COLUMN dataagent_dataset_field.ordinal_position IS '排序位置';
COMMENT ON COLUMN dataagent_dataset_field.datasource_id IS '来源数据源 ID';
COMMENT ON COLUMN dataagent_dataset_field.source_table_id IS '来源表 ID';
COMMENT ON COLUMN dataagent_dataset_field.source_table_name IS '来源表名';
COMMENT ON COLUMN dataagent_dataset_field.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_dataset_field.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_dataset_field.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_dataset_field_upd_ts ON dataagent_dataset_field;
CREATE TRIGGER trg_dataagent_dataset_field_upd_ts BEFORE UPDATE ON dataagent_dataset_field
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

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

CREATE INDEX IF NOT EXISTS idx_data_dataset_id ON dataagent_dataset_data (dataset_id);

CREATE INDEX IF NOT EXISTS idx_data_row_hash ON dataagent_dataset_data (dataset_id, row_hash);

COMMENT ON TABLE dataagent_dataset_data IS '数据集业务数据表';

COMMENT ON COLUMN dataagent_dataset_data.id IS '主键 ID';
COMMENT ON COLUMN dataagent_dataset_data.dataset_id IS '所属数据集 ID';
COMMENT ON COLUMN dataagent_dataset_data.row_data IS '行数据（JSON 对象，key 为字段名，value 为字段值）';
COMMENT ON COLUMN dataagent_dataset_data.row_hash IS '行数据哈希（用于变更检测）';
COMMENT ON COLUMN dataagent_dataset_data.source_row_number IS '源表原始行号（用于溯源）';
COMMENT ON COLUMN dataagent_dataset_data.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_dataset_data.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_dataset_data.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_dataset_data_upd_ts ON dataagent_dataset_data;
CREATE TRIGGER trg_dataagent_dataset_data_upd_ts BEFORE UPDATE ON dataagent_dataset_data
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
