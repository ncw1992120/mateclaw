-- ============================================================
-- DataAgent 洞察仪表盘：低代码仪表盘 Schema 存储
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用
-- schema_json：仪表盘组件配置 JSON（components 数组）
-- agent_id：AI 解读报告使用的 Agent ID
-- ============================================================

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

CREATE INDEX IF NOT EXISTS idx_insight_workspace_id ON dataagent_insight_dashboard (workspace_id);

CREATE INDEX IF NOT EXISTS idx_insight_status ON dataagent_insight_dashboard (status);

COMMENT ON TABLE dataagent_insight_dashboard IS '洞察仪表盘表';

COMMENT ON COLUMN dataagent_insight_dashboard.id IS '主键 ID';
COMMENT ON COLUMN dataagent_insight_dashboard.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_insight_dashboard.name IS '仪表盘名称';
COMMENT ON COLUMN dataagent_insight_dashboard.description IS '描述';
COMMENT ON COLUMN dataagent_insight_dashboard.schema_json IS '仪表盘 Schema JSON（components 数组）';
COMMENT ON COLUMN dataagent_insight_dashboard.report_content IS 'AI 分析报告内容（HTML 格式）';
COMMENT ON COLUMN dataagent_insight_dashboard.status IS '状态：draft/published';
COMMENT ON COLUMN dataagent_insight_dashboard.agent_id IS 'AI 解读使用的 Agent ID';
COMMENT ON COLUMN dataagent_insight_dashboard.owner_id IS '所有者用户 ID';
COMMENT ON COLUMN dataagent_insight_dashboard.owner_name IS '负责人名称';
COMMENT ON COLUMN dataagent_insight_dashboard.modifier IS '修改人';
COMMENT ON COLUMN dataagent_insight_dashboard.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_insight_dashboard.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_insight_dashboard.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_insight_dashboard_upd_ts ON dataagent_insight_dashboard;
CREATE TRIGGER trg_dataagent_insight_dashboard_upd_ts BEFORE UPDATE ON dataagent_insight_dashboard
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
