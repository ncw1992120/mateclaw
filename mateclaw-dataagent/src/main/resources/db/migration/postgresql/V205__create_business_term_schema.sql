-- ============================================================
-- 业务术语表：术语与同义词统一管理
-- ============================================================
-- 术语（Term）是跨数据源的业务概念统一定义，
-- 同义词（Synonym）作为术语的附属属性，以逗号分隔字段存储。
-- 支持租户隔离、类目分组、层级结构（parent_id）、向量化语义检索。
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_term_name ON dataagent_business_term (tenant_code, term_name, deleted);

CREATE INDEX IF NOT EXISTS idx_term_workspace_id ON dataagent_business_term (workspace_id);

CREATE INDEX IF NOT EXISTS idx_tenant_code ON dataagent_business_term (tenant_code);

CREATE INDEX IF NOT EXISTS idx_category ON dataagent_business_term (tenant_code, category);

CREATE INDEX IF NOT EXISTS idx_parent_id ON dataagent_business_term (parent_id);

CREATE INDEX IF NOT EXISTS dataagent_business_term_idx_status ON dataagent_business_term (status);

COMMENT ON TABLE dataagent_business_term IS '业务术语表';

COMMENT ON COLUMN dataagent_business_term.id IS '主键 ID';
COMMENT ON COLUMN dataagent_business_term.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_business_term.tenant_code IS '租户编码（区分不同业务域）';
COMMENT ON COLUMN dataagent_business_term.term_name IS '术语名称（主术语/标准名）';
COMMENT ON COLUMN dataagent_business_term.synonyms IS '同义词（逗号分隔，如"营收,收入"）';
COMMENT ON COLUMN dataagent_business_term.description IS '术语定义/解释';
COMMENT ON COLUMN dataagent_business_term.calculation_formula IS '计算公式（描述该术语的指标计算逻辑/表达式）';
COMMENT ON COLUMN dataagent_business_term.data_caliber IS '数据口径（统计范围、边界条件、排除规则等）';
COMMENT ON COLUMN dataagent_business_term.data_source IS '数据来源/源系统（如CRM、ERP等）';
COMMENT ON COLUMN dataagent_business_term.owner IS '责任人/归属部门（负责维护该术语定义的准确性）';
COMMENT ON COLUMN dataagent_business_term.business_rule IS '业务规则（约束条件/业务逻辑规则）';
COMMENT ON COLUMN dataagent_business_term.related_terms IS '关联术语ID（逗号分隔，如"101,102"）';
COMMENT ON COLUMN dataagent_business_term.related_metrics_json IS '关联指标引用JSON（[{"id":1,"datasourceId":1,"datasourceName":"CRM","name":"sales_amount","displayName":"销售额"}]）';
COMMENT ON COLUMN dataagent_business_term.related_dimensions_json IS '关联维度引用JSON（[{"id":1,"datasourceId":1,"datasourceName":"CRM","name":"province","displayName":"省份"}]）';
COMMENT ON COLUMN dataagent_business_term.example IS '示例/用例（该术语在实际业务中的使用示例）';
COMMENT ON COLUMN dataagent_business_term.security_level IS '安全分级（公开/内部/机密）';
COMMENT ON COLUMN dataagent_business_term.category IS '分类（如：财务类、客户类）';
COMMENT ON COLUMN dataagent_business_term.parent_id IS '父术语 ID（支持层级结构，顶级为 NULL）';
COMMENT ON COLUMN dataagent_business_term.embedding_text IS '嵌入文本（用于生成向量）';
COMMENT ON COLUMN dataagent_business_term.embedding IS '向量数据（float32小端序序列化）';
COMMENT ON COLUMN dataagent_business_term.embedding_model_id IS '嵌入模型 ID';
COMMENT ON COLUMN dataagent_business_term.status IS '状态：0-停用 / 1-启用';
COMMENT ON COLUMN dataagent_business_term.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_business_term.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_business_term.deleted IS '逻辑删除：0-未删除 / 1-已删除';

DROP TRIGGER IF EXISTS trg_dataagent_business_term_upd_ts ON dataagent_business_term;
CREATE TRIGGER trg_dataagent_business_term_upd_ts BEFORE UPDATE ON dataagent_business_term
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
