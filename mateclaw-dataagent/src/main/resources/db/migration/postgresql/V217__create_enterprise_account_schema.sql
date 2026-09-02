-- ============================================================
-- 企业认证影子账号映射表（平安领航 SSO 集成）
-- ============================================================
-- 企业认证（账密代验）通过后，首次登录在 mate_user 表自动开通影子账号
-- （随机密码、不可本地登录），本表记录本地账号与企业身份的映射关系，
-- 用于运维审计与后续离职禁用联动。
-- 注意：不修改 mateclaw-server 的既有表结构，本迁移归属 DataAgent 自有迁移集。
-- ============================================================

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_enterprise_username ON dataagent_enterprise_account (username);

CREATE INDEX IF NOT EXISTS idx_enterprise_principal ON dataagent_enterprise_account (principal_name);

COMMENT ON TABLE dataagent_enterprise_account IS '企业认证影子账号映射表';

COMMENT ON COLUMN dataagent_enterprise_account.id IS '主键 ID';
COMMENT ON COLUMN dataagent_enterprise_account.username IS '本地影子账号用户名（= 域账号）';
COMMENT ON COLUMN dataagent_enterprise_account.principal_name IS '企业侧唯一标识（领航 PRINCIPAL_NAME）';
COMMENT ON COLUMN dataagent_enterprise_account.source IS '身份来源：PILOT（领航 UM/AD）';
COMMENT ON COLUMN dataagent_enterprise_account.status IS '状态：ACTIVE / DISABLED';
COMMENT ON COLUMN dataagent_enterprise_account.last_login_at IS '最近企业登录时间';
COMMENT ON COLUMN dataagent_enterprise_account.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_enterprise_account.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_enterprise_account.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_enterprise_account_upd_ts ON dataagent_enterprise_account;
CREATE TRIGGER trg_dataagent_enterprise_account_upd_ts BEFORE UPDATE ON dataagent_enterprise_account
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
