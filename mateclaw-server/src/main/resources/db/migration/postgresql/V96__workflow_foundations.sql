-- V96: Foundational schema for the workflow runtime.
-- Eight tables establish workflow identity (workflow + immutable revisions),
-- run state (run + per-step rows + durable pause rows for await_approval),
-- payload URI storage with inline / filesystem fallback, and trigger
-- definitions paired with a dedup-window table for envelope-based event
-- governance. CREATE TABLE IF NOT EXISTS is itself idempotent on MySQL.

-- 1. Stable workflow identity + draft (1:1 with workflow row).

CREATE TABLE IF NOT EXISTS mate_workflow (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    draft_json TEXT,
    draft_schema_version VARCHAR(8),
    draft_updated_by BIGINT,
    draft_updated_at TIMESTAMP(3),
    latest_revision_id BIGINT,
    created_by BIGINT,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_workspace_name ON mate_workflow (workspace_id, name, deleted);

DROP TRIGGER IF EXISTS trg_mate_workflow_upd_ts ON mate_workflow;
CREATE TRIGGER trg_mate_workflow_upd_ts BEFORE UPDATE ON mate_workflow
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS mate_workflow_revision (
    id BIGINT NOT NULL,
    workflow_id BIGINT NOT NULL,
    revision INTEGER NOT NULL,
    graph_json TEXT NOT NULL,
    schema_version VARCHAR(8) NOT NULL,
    published_note VARCHAR(512),
    published_by BIGINT,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_revision ON mate_workflow_revision (workflow_id, revision);

CREATE TABLE IF NOT EXISTS mate_workflow_run (
    id BIGINT NOT NULL,
    workflow_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    triggered_by VARCHAR(32),
    triggered_meta TEXT,
    initial_input_ref VARCHAR(256),
    final_output_ref VARCHAR(256),
    error_message VARCHAR(2048),
    started_at TIMESTAMP(3),
    completed_at TIMESTAMP(3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_run_started ON mate_workflow_run (workflow_id, started_at);

CREATE TABLE IF NOT EXISTS mate_workflow_run_step (
    id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    step_index INTEGER NOT NULL,
    iteration_index INTEGER,
    step_name VARCHAR(128),
    agent_id BIGINT,
    state VARCHAR(16),
    input_ref VARCHAR(256),
    output_ref VARCHAR(256),
    output_summary VARCHAR(512),
    output_content_type VARCHAR(64),
    error_message VARCHAR(2048),
    duration_ms BIGINT,
    token_input INTEGER,
    token_output INTEGER,
    started_at TIMESTAMP(3),
    completed_at TIMESTAMP(3),
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_workflow_run_step ON mate_workflow_run_step (run_id, step_index, iteration_index);

CREATE TABLE IF NOT EXISTS mate_workflow_run_pause (
    id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    pause_kind VARCHAR(32) NOT NULL,
    pause_token VARCHAR(128) NOT NULL,
    external_approval_id BIGINT,
    paused_at TIMESTAMP(3) NOT NULL,
    resume_deadline TIMESTAMP(3),
    resume_payload_ref VARCHAR(256),
    resumed_at TIMESTAMP(3),
    resume_outcome VARCHAR(32),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_pause_run_step ON mate_workflow_run_pause (run_id, step_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_pause_token ON mate_workflow_run_pause (pause_token);

CREATE INDEX IF NOT EXISTS idx_workflow_pause_external_approval ON mate_workflow_run_pause (external_approval_id);

CREATE INDEX IF NOT EXISTS idx_workflow_pause_open_deadline ON mate_workflow_run_pause (resumed_at, resume_deadline);

CREATE TABLE IF NOT EXISTS mate_workflow_payload (
    id BIGINT NOT NULL,
    payload_uri VARCHAR(256) NOT NULL,
    workspace_id BIGINT NOT NULL,
    content_bytes BYTEA,
    storage_kind VARCHAR(16) NOT NULL,
    storage_ref VARCHAR(512),
    content_type VARCHAR(64),
    sha256 CHAR(64),
    size_bytes BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_payload_uri ON mate_workflow_payload (payload_uri);

CREATE INDEX IF NOT EXISTS idx_workflow_payload_workspace_created ON mate_workflow_payload (workspace_id, created_at);

CREATE TABLE IF NOT EXISTS mate_trigger (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(128),
    pattern_type VARCHAR(32) NOT NULL,
    pattern_json TEXT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    payload_template TEXT,
    rate_limit_per_min INTEGER NOT NULL DEFAULT 60,
    dedup_window_secs INTEGER NOT NULL DEFAULT 60,
    bot_self_filter BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    fire_count BIGINT NOT NULL DEFAULT 0,
    max_fires BIGINT NOT NULL DEFAULT 0,
    last_fired_at TIMESTAMP(3),
    pattern_version BIGINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_trigger_workspace_enabled ON mate_trigger (workspace_id, enabled, deleted);

CREATE INDEX IF NOT EXISTS idx_trigger_target ON mate_trigger (target_type, target_id);

DROP TRIGGER IF EXISTS trg_mate_trigger_upd_ts ON mate_trigger;
CREATE TRIGGER trg_mate_trigger_upd_ts BEFORE UPDATE ON mate_trigger
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS mate_trigger_event (
    id BIGINT NOT NULL,
    trigger_id BIGINT NOT NULL,
    dedup_key VARCHAR(128) NOT NULL,
    received_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_trigger_dedup ON mate_trigger_event (trigger_id, dedup_key);

CREATE INDEX IF NOT EXISTS idx_trigger_event_expires ON mate_trigger_event (expires_at);
