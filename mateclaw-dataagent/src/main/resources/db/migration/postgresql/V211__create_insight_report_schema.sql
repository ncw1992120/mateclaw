-- ============================================================
-- DataAgent 洞察报告：已发布报告独立存储
-- ============================================================
-- dashboard_id：关联的仪表盘 ID，报告内容从仪表盘复制而来
-- workspace_id：所属工作区 ID，资源隔离用
-- report_content：报告 HTML 内容（从仪表盘 report_content 字段复制）
-- status：报告状态 draft/published
-- ============================================================

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

CREATE INDEX IF NOT EXISTS idx_report_workspace_id ON dataagent_insight_report (workspace_id);

CREATE INDEX IF NOT EXISTS idx_report_dashboard_id ON dataagent_insight_report (dashboard_id);

CREATE INDEX IF NOT EXISTS idx_report_status ON dataagent_insight_report (status);

COMMENT ON TABLE dataagent_insight_report IS '洞察报告表';

COMMENT ON COLUMN dataagent_insight_report.id IS '主键 ID';
COMMENT ON COLUMN dataagent_insight_report.dashboard_id IS '关联的仪表盘 ID';
COMMENT ON COLUMN dataagent_insight_report.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_insight_report.name IS '报告名称';
COMMENT ON COLUMN dataagent_insight_report.description IS '描述';
COMMENT ON COLUMN dataagent_insight_report.report_content IS '报告 HTML 内容';
COMMENT ON COLUMN dataagent_insight_report.echarts_options IS 'ECharts option 数据（JSON 格式）';
COMMENT ON COLUMN dataagent_insight_report.status IS '状态：draft/published';
COMMENT ON COLUMN dataagent_insight_report.owner_id IS '所有者用户 ID';
COMMENT ON COLUMN dataagent_insight_report.owner_name IS '负责人名称';
COMMENT ON COLUMN dataagent_insight_report.modifier IS '修改人';
COMMENT ON COLUMN dataagent_insight_report.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_insight_report.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_insight_report.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_insight_report_upd_ts ON dataagent_insight_report;
CREATE TRIGGER trg_dataagent_insight_report_upd_ts BEFORE UPDATE ON dataagent_insight_report
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
