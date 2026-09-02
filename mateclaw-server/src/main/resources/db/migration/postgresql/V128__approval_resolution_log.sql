-- V128: Approval resolution log table (MySQL dialect).

CREATE TABLE IF NOT EXISTS mate_approval_resolution_log (
    id BIGINT NOT NULL,
    workspace_id BIGINT DEFAULT NULL,
    conversation_id VARCHAR(128) DEFAULT NULL,
    agent_id VARCHAR(64) DEFAULT NULL,
    user_id VARCHAR(64) DEFAULT NULL,
    tool_call_id VARCHAR(64) DEFAULT NULL,
    tool_name VARCHAR(128) NOT NULL,
    max_severity VARCHAR(16) DEFAULT NULL,
    rule_ids VARCHAR(512) DEFAULT NULL,
    decision_source VARCHAR(24) NOT NULL,
    grant_id BIGINT DEFAULT NULL,
    pending_id VARCHAR(32) DEFAULT NULL,
    args_preview VARCHAR(500) DEFAULT NULL,
    note VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_resolution_workspace_time ON mate_approval_resolution_log (workspace_id, create_time);

CREATE INDEX IF NOT EXISTS idx_resolution_grant ON mate_approval_resolution_log (grant_id);

CREATE INDEX IF NOT EXISTS idx_resolution_pending ON mate_approval_resolution_log (pending_id);
