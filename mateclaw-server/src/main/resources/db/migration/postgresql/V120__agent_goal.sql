-- Persistent goal — see h2/V120__agent_goal.sql for full design notes.
--
-- MySQL-specific differences vs H2:
--   1. CLOB -> LONGTEXT
--   2. TIMESTAMP -> DATETIME(3) for millisecond precision matching V117
--   3. BOOLEAN -> TINYINT(1)
--   4. H2 uses a PREDICATE unique index for "one active goal per
--      conversation"; MySQL InnoDB does not support filtered indexes,
--      so we emulate it with a virtual generated column that is NULL for
--      non-active rows + a plain unique index. NULLs are excluded from
--      uniqueness enforcement by MySQL's default index semantics.

CREATE TABLE IF NOT EXISTS mate_agent_goal (
    id BIGINT NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    agent_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    exit_criteria TEXT,
    success_check_prompt TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    turn_budget INTEGER NOT NULL DEFAULT 20,
    turns_used INTEGER NOT NULL DEFAULT 0,
    llm_call_budget INTEGER NOT NULL DEFAULT 200,
    agent_llm_calls_used INTEGER NOT NULL DEFAULT 0,
    eval_llm_calls_used INTEGER NOT NULL DEFAULT 0,
    progress_summary TEXT,
    completion_score DOUBLE PRECISION,
    last_evaluation_at TIMESTAMP(3),
    auto_followup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    followup_cooldown_seconds INTEGER NOT NULL DEFAULT 0,
    last_followup_at TIMESTAMP(3),
    active_conv_key             VARCHAR(80)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'active' AND deleted = 0
                 THEN conversation_id ELSE NULL END
        ) STORED,
    version INTEGER NOT NULL DEFAULT 0,
    deleted SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL,
    update_time TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_goal_active_conv ON mate_agent_goal (active_conv_key);

CREATE INDEX IF NOT EXISTS idx_agent_goal_conv ON mate_agent_goal (conversation_id, status);

CREATE INDEX IF NOT EXISTS idx_agent_goal_status ON mate_agent_goal (status, last_evaluation_at);

CREATE INDEX IF NOT EXISTS idx_agent_goal_owner ON mate_agent_goal (created_by, status);

CREATE TABLE IF NOT EXISTS mate_agent_goal_event (
    id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    message_id BIGINT,
    detail_json TEXT,
    create_time TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_agent_goal_event_goal ON mate_agent_goal_event (goal_id, id);
