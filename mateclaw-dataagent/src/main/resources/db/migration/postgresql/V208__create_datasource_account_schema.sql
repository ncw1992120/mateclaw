-- ============================================================
-- 数据源用户查询账号绑定表
-- ============================================================
-- 每个用户可以为自己绑定的数据源配置独立的查询账号，
-- 查询时优先使用用户自己的查询账号，而非数据源的管理员同步账号。
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_datasource_user ON dataagent_datasource_account (datasource_id, user_id, deleted);

CREATE INDEX IF NOT EXISTS idx_account_workspace_id ON dataagent_datasource_account (workspace_id);

CREATE INDEX IF NOT EXISTS idx_account_user_id ON dataagent_datasource_account (user_id);

COMMENT ON TABLE dataagent_datasource_account IS '数据源用户查询账号绑定表';

COMMENT ON COLUMN dataagent_datasource_account.id IS '主键 ID';
COMMENT ON COLUMN dataagent_datasource_account.datasource_id IS '关联数据源 ID';
COMMENT ON COLUMN dataagent_datasource_account.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_datasource_account.user_id IS '用户 ID';
COMMENT ON COLUMN dataagent_datasource_account.query_username IS '查询用户名';
COMMENT ON COLUMN dataagent_datasource_account.query_password IS '查询密码（AES 加密存储）';
COMMENT ON COLUMN dataagent_datasource_account.status IS '状态：0-停用 / 1-启用';
COMMENT ON COLUMN dataagent_datasource_account.last_test_time IS '最近测试时间';
COMMENT ON COLUMN dataagent_datasource_account.last_test_ok IS '最近测试结果';
COMMENT ON COLUMN dataagent_datasource_account.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_datasource_account.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_datasource_account.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_datasource_account_upd_ts ON dataagent_datasource_account;
CREATE TRIGGER trg_dataagent_datasource_account_upd_ts BEFORE UPDATE ON dataagent_datasource_account
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
