-- ============================================================
-- DataAgent 多源异构数据接入：数据源、表元数据、字段元数据
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- owner_id：数据源创建者用户 ID，列表查询按此过滤（不同用户仅可见自己配置的数据源）
-- ============================================================

-- 1. 数据源主表

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

CREATE INDEX IF NOT EXISTS idx_datasource_workspace_id ON dataagent_datasource (workspace_id);

CREATE INDEX IF NOT EXISTS idx_datasource_owner_id ON dataagent_datasource (owner_id);

CREATE INDEX IF NOT EXISTS idx_datasource_source_type ON dataagent_datasource (source_type);

CREATE INDEX IF NOT EXISTS idx_datasource_enabled ON dataagent_datasource (enabled);

COMMENT ON TABLE dataagent_datasource IS '数据源主表';

COMMENT ON COLUMN dataagent_datasource.id IS '主键 ID';
COMMENT ON COLUMN dataagent_datasource.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_datasource.owner_id IS '数据源创建者用户 ID（权限隔离用，列表查询按此过滤）';
COMMENT ON COLUMN dataagent_datasource.name IS '数据源名称';
COMMENT ON COLUMN dataagent_datasource.description IS '描述';
COMMENT ON COLUMN dataagent_datasource.source_type IS '数据源类型：mysql/postgresql/oracle/snowflake/bigquery/redshift/clickhouse/doris/mongodb/elasticsearch/csv/excel/parquet/api/kafka';
COMMENT ON COLUMN dataagent_datasource.host IS '主机地址（通用字段，可作为历史数据兜底）';
COMMENT ON COLUMN dataagent_datasource.product_host IS '产品层服务地址（Aloudata anymetrics，端口默认 8083）';
COMMENT ON COLUMN dataagent_datasource.semantic_host IS '语义层服务地址（Aloudata semantic，端口默认 8085）';
COMMENT ON COLUMN dataagent_datasource.port IS '端口';
COMMENT ON COLUMN dataagent_datasource.database_name IS '数据库名称/文件路径/接口地址/Topic';
COMMENT ON COLUMN dataagent_datasource.username IS '用户名';
COMMENT ON COLUMN dataagent_datasource.password IS '密码（AES 加密存储）';
COMMENT ON COLUMN dataagent_datasource.connection_params IS '连接参数（JSON 格式）';
COMMENT ON COLUMN dataagent_datasource.schema_name IS 'Schema 名称';
COMMENT ON COLUMN dataagent_datasource.meta_shared IS '元数据是否共享（1=同工作区所有用户可见，0=仅 owner 可见）';
COMMENT ON COLUMN dataagent_datasource.enabled IS '是否启用';
COMMENT ON COLUMN dataagent_datasource.last_test_time IS '最近测试时间';
COMMENT ON COLUMN dataagent_datasource.last_test_ok IS '最近测试结果';
COMMENT ON COLUMN dataagent_datasource.schema_status IS 'Schema 发现状态：pending/running/completed/failed';
COMMENT ON COLUMN dataagent_datasource.last_schema_discovery_time IS '最近 Schema 发现时间';
COMMENT ON COLUMN dataagent_datasource.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_datasource.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_datasource.deleted IS '逻辑删除标记';

-- 模拟 MySQL ON UPDATE CURRENT_TIMESTAMP：应用未显式改动 update_time 时自动刷新
CREATE OR REPLACE FUNCTION set_update_time() RETURNS trigger AS $$
BEGIN
    IF NEW.update_time IS NULL OR NEW.update_time = OLD.update_time THEN
        NEW.update_time := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_dataagent_datasource_upd_ts ON dataagent_datasource;
CREATE TRIGGER trg_dataagent_datasource_upd_ts BEFORE UPDATE ON dataagent_datasource
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

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

CREATE INDEX IF NOT EXISTS idx_table_datasource_id ON dataagent_datasource_tables (datasource_id);

CREATE INDEX IF NOT EXISTS idx_table_name ON dataagent_datasource_tables (datasource_id, table_name);

COMMENT ON TABLE dataagent_datasource_tables IS '数据源表元数据';

COMMENT ON COLUMN dataagent_datasource_tables.id IS '主键 ID';
COMMENT ON COLUMN dataagent_datasource_tables.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_datasource_tables.table_name IS '表名';
COMMENT ON COLUMN dataagent_datasource_tables.table_comment IS '表注释/说明';
COMMENT ON COLUMN dataagent_datasource_tables.table_type IS '表类型：table/view/materialized_view/external';
COMMENT ON COLUMN dataagent_datasource_tables.row_count IS '估算行数';
COMMENT ON COLUMN dataagent_datasource_tables.data_size_bytes IS '估算数据大小（字节）';
COMMENT ON COLUMN dataagent_datasource_tables.schema_name IS 'Schema 名称';
COMMENT ON COLUMN dataagent_datasource_tables.engine IS '引擎/存储类型';
COMMENT ON COLUMN dataagent_datasource_tables.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_datasource_tables.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_datasource_tables.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_datasource_tables_upd_ts ON dataagent_datasource_tables;
CREATE TRIGGER trg_dataagent_datasource_tables_upd_ts BEFORE UPDATE ON dataagent_datasource_tables
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

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

CREATE INDEX IF NOT EXISTS idx_column_datasource_id ON dataagent_datasource_columns (datasource_id);

CREATE INDEX IF NOT EXISTS idx_column_table_id ON dataagent_datasource_columns (table_id);

CREATE INDEX IF NOT EXISTS idx_column_name ON dataagent_datasource_columns (table_id, column_name);

COMMENT ON TABLE dataagent_datasource_columns IS '数据源字段元数据';

COMMENT ON COLUMN dataagent_datasource_columns.id IS '主键 ID';
COMMENT ON COLUMN dataagent_datasource_columns.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_datasource_columns.table_id IS '关联表 ID';
COMMENT ON COLUMN dataagent_datasource_columns.column_name IS '字段名';
COMMENT ON COLUMN dataagent_datasource_columns.column_comment IS '字段注释';
COMMENT ON COLUMN dataagent_datasource_columns.data_type IS '字段数据类型';
COMMENT ON COLUMN dataagent_datasource_columns.column_size IS '字段长度';
COMMENT ON COLUMN dataagent_datasource_columns.decimal_digits IS '小数位数';
COMMENT ON COLUMN dataagent_datasource_columns.nullable IS '是否可为空';
COMMENT ON COLUMN dataagent_datasource_columns.primary_key IS '是否为主键';
COMMENT ON COLUMN dataagent_datasource_columns.indexed IS '是否为索引字段';
COMMENT ON COLUMN dataagent_datasource_columns.default_value IS '默认值';
COMMENT ON COLUMN dataagent_datasource_columns.ordinal_position IS '排序位置';
COMMENT ON COLUMN dataagent_datasource_columns.foreign_key_table IS '外键关联表名';
COMMENT ON COLUMN dataagent_datasource_columns.foreign_key_column IS '外键关联字段名';
COMMENT ON COLUMN dataagent_datasource_columns.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_datasource_columns.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_datasource_columns.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_datasource_columns_upd_ts ON dataagent_datasource_columns;
CREATE TRIGGER trg_dataagent_datasource_columns_upd_ts BEFORE UPDATE ON dataagent_datasource_columns
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
