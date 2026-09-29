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

CREATE TRIGGER trg_dataagent_user_uid_mapping_upd_ts BEFORE UPDATE ON dataagent_user_uid_mapping
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
