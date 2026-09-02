-- ============================================================
-- DataAgent 语义层：字段级语义模型、逻辑外键关系、Schema 嵌入向量
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

-- 1. 字段级语义模型表

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_semantic_model ON dataagent_semantic_model (datasource_id, table_name, column_name, deleted);

CREATE INDEX IF NOT EXISTS idx_semantic_workspace_id ON dataagent_semantic_model (workspace_id);

CREATE INDEX IF NOT EXISTS idx_semantic_datasource_id ON dataagent_semantic_model (datasource_id);

CREATE INDEX IF NOT EXISTS idx_semantic_table_name ON dataagent_semantic_model (datasource_id, table_name);

COMMENT ON TABLE dataagent_semantic_model IS '字段级语义模型';

COMMENT ON COLUMN dataagent_semantic_model.id IS '主键 ID';
COMMENT ON COLUMN dataagent_semantic_model.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_semantic_model.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_semantic_model.table_name IS '表名';
COMMENT ON COLUMN dataagent_semantic_model.column_name IS '字段名';
COMMENT ON COLUMN dataagent_semantic_model.business_name IS '业务别名（如"客户满意度分数"）';
COMMENT ON COLUMN dataagent_semantic_model.business_description IS '业务描述，直接用于 Prompt';
COMMENT ON COLUMN dataagent_semantic_model.synonyms IS '同义词（逗号分隔，如"满意度,客户评分"）';
COMMENT ON COLUMN dataagent_semantic_model.data_type IS '物理数据类型';
COMMENT ON COLUMN dataagent_semantic_model.column_comment IS '数据库原始注释';
COMMENT ON COLUMN dataagent_semantic_model.example_values IS '示例值（逗号分隔）';
COMMENT ON COLUMN dataagent_semantic_model.enum_values IS '枚举值 JSON（如 {"0":"待支付","1":"已支付"}）';
COMMENT ON COLUMN dataagent_semantic_model.unit IS '单位（如 °C、m/s、%）';
COMMENT ON COLUMN dataagent_semantic_model.value_range IS '值域范围（如 0~100）';
COMMENT ON COLUMN dataagent_semantic_model.status IS '状态：0-停用 / 1-启用';
COMMENT ON COLUMN dataagent_semantic_model.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_semantic_model.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_semantic_model.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_semantic_model_upd_ts ON dataagent_semantic_model;
CREATE TRIGGER trg_dataagent_semantic_model_upd_ts BEFORE UPDATE ON dataagent_semantic_model
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

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

CREATE INDEX IF NOT EXISTS idx_relation_workspace_id ON dataagent_logical_relation (workspace_id);

CREATE INDEX IF NOT EXISTS idx_relation_datasource_id ON dataagent_logical_relation (datasource_id);

CREATE INDEX IF NOT EXISTS idx_relation_source_table ON dataagent_logical_relation (datasource_id, source_table_name);

CREATE INDEX IF NOT EXISTS idx_relation_target_table ON dataagent_logical_relation (datasource_id, target_table_name);

COMMENT ON TABLE dataagent_logical_relation IS '逻辑外键关系';

COMMENT ON COLUMN dataagent_logical_relation.id IS '主键 ID';
COMMENT ON COLUMN dataagent_logical_relation.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_logical_relation.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_logical_relation.source_table_name IS '源表名';
COMMENT ON COLUMN dataagent_logical_relation.source_column_name IS '源字段名';
COMMENT ON COLUMN dataagent_logical_relation.target_table_name IS '目标表名';
COMMENT ON COLUMN dataagent_logical_relation.target_column_name IS '目标字段名';
COMMENT ON COLUMN dataagent_logical_relation.relation_type IS '关系类型：1:1 / 1:N / N:1';
COMMENT ON COLUMN dataagent_logical_relation.description IS '业务描述（如"订单关联客户"）';
COMMENT ON COLUMN dataagent_logical_relation.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_logical_relation.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_logical_relation.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_logical_relation_upd_ts ON dataagent_logical_relation;
CREATE TRIGGER trg_dataagent_logical_relation_upd_ts BEFORE UPDATE ON dataagent_logical_relation
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

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

CREATE INDEX IF NOT EXISTS idx_embedding_datasource_id ON dataagent_schema_embedding (datasource_id);

CREATE INDEX IF NOT EXISTS idx_embedding_table_name ON dataagent_schema_embedding (datasource_id, table_name);

COMMENT ON TABLE dataagent_schema_embedding IS 'Schema 嵌入向量';

COMMENT ON COLUMN dataagent_schema_embedding.id IS '主键 ID';
COMMENT ON COLUMN dataagent_schema_embedding.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_schema_embedding.table_name IS '表名';
COMMENT ON COLUMN dataagent_schema_embedding.embedding_text IS '嵌入输入文本';
COMMENT ON COLUMN dataagent_schema_embedding.embedding IS '向量数据（float32 小端序序列化）';
COMMENT ON COLUMN dataagent_schema_embedding.embedding_model_id IS '使用的嵌入模型 ID';
COMMENT ON COLUMN dataagent_schema_embedding.embedding_text_version IS '嵌入文本格式版本';
COMMENT ON COLUMN dataagent_schema_embedding.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_schema_embedding.update_time IS '更新时间';

DROP TRIGGER IF EXISTS trg_dataagent_schema_embedding_upd_ts ON dataagent_schema_embedding;
CREATE TRIGGER trg_dataagent_schema_embedding_upd_ts BEFORE UPDATE ON dataagent_schema_embedding
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
