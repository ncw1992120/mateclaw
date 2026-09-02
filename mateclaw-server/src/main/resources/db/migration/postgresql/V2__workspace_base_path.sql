-- V2: Upgrade schema for databases created before Flyway was introduced.
-- MySQL does NOT support `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (MariaDB-only).
-- We use INFORMATION_SCHEMA + dynamic SQL as an idempotent replacement so this migration
-- is safe on BOTH: (a) fresh MySQL installs whose V1 baseline already contains the columns,
-- and (b) legacy installs bootstrapped from the old schema.sql that predates those columns.

CREATE TABLE IF NOT EXISTS mate_workspace (
    id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    owner_id BIGINT,
    settings_json TEXT,
    base_path VARCHAR(512),
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    owner_id BIGINT,
    settings_json TEXT,
    base_path VARCHAR(512),
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS mate_workspace_uk_workspace_slug ON mate_workspace (slug);

ALTER TABLE mate_workspace ADD COLUMN IF NOT EXISTS base_path VARCHAR(512);

CREATE TABLE IF NOT EXISTS mate_workspace_member (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'member',
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'member',
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS mate_workspace_member_idx_ws_member_workspace ON mate_workspace_member (workspace_id);

CREATE INDEX IF NOT EXISTS mate_workspace_member_idx_ws_member_user ON mate_workspace_member (user_id);

ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE mate_channel ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE mate_tool ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

ALTER TABLE mate_skill ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS mate_workspace_file (
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    filename VARCHAR(256) NOT NULL,
    content TEXT,
    file_size BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    filename VARCHAR(256) NOT NULL,
    content TEXT,
    file_size BIGINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS mate_workspace_file_idx_workspace_file_agent ON mate_workspace_file (agent_id);

CREATE INDEX IF NOT EXISTS mate_workspace_file_idx_workspace_file_agent_enabled ON mate_workspace_file (agent_id, enabled);

CREATE TABLE IF NOT EXISTS mate_usage_daily (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    agent_id BIGINT,
    stat_date DATE NOT NULL,
    conversation_count INTEGER DEFAULT 0,
    message_count INTEGER DEFAULT 0,
    total_tokens BIGINT DEFAULT 0,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    tool_call_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    create_time TIMESTAMP NOT NULL,
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    conversation_count INTEGER NOT NULL DEFAULT 0,
    message_count INTEGER NOT NULL DEFAULT 0,
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS mate_usage_daily_uk_usage_daily ON mate_usage_daily (workspace_id, agent_id, stat_date);

CREATE TABLE IF NOT EXISTS mate_audit_event (
    id BIGINT NOT NULL,
    workspace_id BIGINT,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    resource_name VARCHAR(256),
    detail_json TEXT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(256),
    create_time TIMESTAMP NOT NULL,
    id BIGINT NOT NULL,
    workspace_id BIGINT,
    user_id BIGINT,
    username VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),
    detail TEXT,
    ip_address VARCHAR(64),
    create_time TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS mate_audit_event_idx_audit_ws_time ON mate_audit_event (workspace_id, create_time);

CREATE INDEX IF NOT EXISTS mate_audit_event_idx_audit_user ON mate_audit_event (user_id);

CREATE INDEX IF NOT EXISTS mate_audit_event_idx_audit_resource ON mate_audit_event (resource_type, resource_id);

ALTER TABLE mate_model_provider ADD COLUMN IF NOT EXISTS auth_type VARCHAR(16) NOT NULL DEFAULT 'api_key';

ALTER TABLE mate_model_provider ADD COLUMN IF NOT EXISTS oauth_access_token TEXT;

ALTER TABLE mate_model_provider ADD COLUMN IF NOT EXISTS oauth_refresh_token TEXT;

ALTER TABLE mate_model_provider ADD COLUMN IF NOT EXISTS oauth_expires_at BIGINT;

ALTER TABLE mate_model_provider ADD COLUMN IF NOT EXISTS oauth_account_id VARCHAR(128);

CREATE TABLE IF NOT EXISTS mate_agent_skill (
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_json TEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS mate_agent_skill_uk_agent_skill ON mate_agent_skill (agent_id, skill_id);

CREATE TABLE IF NOT EXISTS mate_agent_tool (
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS mate_agent_tool_uk_agent_tool ON mate_agent_tool (agent_id, tool_name);

CREATE TABLE IF NOT EXISTS mate_memory_recall (
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    filename VARCHAR(256) NOT NULL,
    snippet_hash VARCHAR(64),
    snippet_preview VARCHAR(512),
    recall_count INTEGER NOT NULL DEFAULT 0,
    daily_count INTEGER NOT NULL DEFAULT 0,
    query_hashes TEXT,
    score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    last_recalled_at TIMESTAMP,
    promoted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    filename VARCHAR(256) NOT NULL,
    content TEXT,
    tags VARCHAR(512),
    score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    last_recalled_at TIMESTAMP,
    promoted BOOLEAN NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS mate_memory_recall_idx_memory_recall_agent ON mate_memory_recall (agent_id);

CREATE INDEX IF NOT EXISTS mate_memory_recall_idx_memory_recall_agent_file ON mate_memory_recall (agent_id, filename);

CREATE INDEX IF NOT EXISTS mate_memory_recall_idx_memory_recall_score ON mate_memory_recall (agent_id, score);

CREATE INDEX IF NOT EXISTS mate_memory_recall_idx_memory_recall_candidates ON mate_memory_recall (agent_id, promoted, deleted);
