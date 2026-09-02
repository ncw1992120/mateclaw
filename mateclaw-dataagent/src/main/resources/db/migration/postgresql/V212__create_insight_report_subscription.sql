-- ============================================================
-- DataAgent 洞察报告订阅：用户订阅已发布的报告
-- ============================================================
-- report_id：关联的报告 ID
-- user_id：订阅用户 ID
-- workspace_id：所属工作区 ID，资源隔离用
-- 唯一索引 (report_id, user_id)：防止同一用户重复订阅同一报告
-- ============================================================

CREATE TABLE IF NOT EXISTS dataagent_insight_report_subscription (
    id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_report_user ON dataagent_insight_report_subscription (report_id, user_id);

CREATE INDEX IF NOT EXISTS idx_subscription_workspace_id ON dataagent_insight_report_subscription (workspace_id);

CREATE INDEX IF NOT EXISTS idx_subscription_user_id ON dataagent_insight_report_subscription (user_id);

COMMENT ON TABLE dataagent_insight_report_subscription IS '洞察报告订阅表';

COMMENT ON COLUMN dataagent_insight_report_subscription.id IS '主键 ID';
COMMENT ON COLUMN dataagent_insight_report_subscription.report_id IS '报告 ID';
COMMENT ON COLUMN dataagent_insight_report_subscription.user_id IS '订阅用户 ID';
COMMENT ON COLUMN dataagent_insight_report_subscription.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_insight_report_subscription.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_insight_report_subscription.deleted IS '逻辑删除标记';
