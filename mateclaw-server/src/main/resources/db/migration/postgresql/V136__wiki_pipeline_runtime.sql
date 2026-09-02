-- V136: Wiki pipeline runtime — definitions, runs, and per-step runs.
-- See the H2 file for design rationale.

CREATE TABLE IF NOT EXISTS mate_wiki_pipeline_definition (
    id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    owner_agent_id BIGINT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    trigger_config_json TEXT,
    steps_json TEXT NOT NULL,
    dedup_window_seconds INTEGER NOT NULL DEFAULT 0,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wiki_pipeline_def_name ON mate_wiki_pipeline_definition (kb_id, name, deleted);

CREATE INDEX IF NOT EXISTS idx_wiki_pipeline_def_trigger ON mate_wiki_pipeline_definition (kb_id, trigger_type, enabled, deleted);

CREATE TABLE IF NOT EXISTS mate_wiki_pipeline_run (
    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    trigger_subject VARCHAR(128) NOT NULL,
    trigger_bucket VARCHAR(64) NOT NULL,
    trigger_payload_json TEXT,
    input_json TEXT,
    output_json TEXT,
    error_message VARCHAR(2048),
    started_at TIMESTAMP(3),
    finished_at TIMESTAMP(3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wiki_pipeline_run_dedup ON mate_wiki_pipeline_run (definition_id, trigger_type, trigger_subject, trigger_bucket, deleted);

CREATE INDEX IF NOT EXISTS idx_wiki_pipeline_run_def ON mate_wiki_pipeline_run (definition_id, status);

CREATE TABLE IF NOT EXISTS mate_wiki_pipeline_step_run (
    id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    executor VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    error_message VARCHAR(2048),
    started_at TIMESTAMP(3),
    finished_at TIMESTAMP(3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_wiki_pipeline_step_run ON mate_wiki_pipeline_step_run (run_id, status);
