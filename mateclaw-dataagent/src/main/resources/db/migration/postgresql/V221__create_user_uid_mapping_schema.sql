-- ============================================================
-- 用户 Aloudata UID 映射表
-- ============================================================
-- 从外部用户系统（MySQL/PostgreSQL 源库）定时同步
-- 「登录名 + 租户 → Aloudata UID」映射关系，
-- 用户问数时可使用映射的 UID 认证，无需手动绑定查询账号；
-- 手动绑定（dataagent_datasource_account）优先，本表映射兜底。
-- 唯一键：(username, tenant_id)，同一租户下多个数据源共享一份映射。
-- ============================================================

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

CREATE UNIQUE INDEX IF NOT EXISTS uk_username_tenant ON dataagent_user_uid_mapping (username, tenant_id);

CREATE INDEX IF NOT EXISTS idx_uid_mapping_tenant ON dataagent_user_uid_mapping (tenant_id);

COMMENT ON TABLE dataagent_user_uid_mapping IS '用户 Aloudata UID 映射表';

COMMENT ON COLUMN dataagent_user_uid_mapping.id IS '主键 ID';
COMMENT ON COLUMN dataagent_user_uid_mapping.username IS '登录名（对齐 mate_user.username）';
COMMENT ON COLUMN dataagent_user_uid_mapping.tenant_id IS 'Aloudata 租户 ID（对齐数据源租户配置）';
COMMENT ON COLUMN dataagent_user_uid_mapping.nickname IS '昵称（仅展示用，源库提供，缺失时回落 mate_user.nickname）';
COMMENT ON COLUMN dataagent_user_uid_mapping.aloudata_uid IS 'Aloudata UID 认证值（AES 加密存储）';
COMMENT ON COLUMN dataagent_user_uid_mapping.status IS '状态：0-停用 / 1-启用';
COMMENT ON COLUMN dataagent_user_uid_mapping.sync_source IS '映射来源：jdbc_sync-定时同步 / manual-手动录入';
COMMENT ON COLUMN dataagent_user_uid_mapping.sync_time IS '最近同步时间';
COMMENT ON COLUMN dataagent_user_uid_mapping.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_user_uid_mapping.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_user_uid_mapping.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_user_uid_mapping_upd_ts ON dataagent_user_uid_mapping;
CREATE TRIGGER trg_dataagent_user_uid_mapping_upd_ts BEFORE UPDATE ON dataagent_user_uid_mapping
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
