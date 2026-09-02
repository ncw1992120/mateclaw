-- V137: Per-owner memory isolation with a three-state visibility scope (MySQL).
--
-- See the H2 counterpart for the full rationale. MySQL has no
-- "ADD COLUMN IF NOT EXISTS", so each column/index is guarded with an
-- INFORMATION_SCHEMA existence check + prepared statement for idempotency.
--
-- Existing rows are backfilled to scope='TEAM' by the NOT NULL DEFAULT so that
-- upgrading does NOT hide previously-shared memory.

-- ---------- mate_workspace_file ----------

ALTER TABLE mate_workspace_file ADD COLUMN IF NOT EXISTS owner_key VARCHAR(128);

ALTER TABLE mate_workspace_file ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'TEAM';

CREATE INDEX IF NOT EXISTS idx_workspace_file_scope_owner ON mate_workspace_file (agent_id, scope, owner_key);

UPDATE mate_workspace_file SET owner_key = '' WHERE owner_key IS NULL;

DELETE FROM mate_workspace_file
WHERE id NOT IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id
        FROM mate_workspace_file
        GROUP BY agent_id, filename, owner_key
    ) t
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_file_owner ON mate_workspace_file (agent_id, filename, owner_key);

ALTER TABLE mate_memory_recall ADD COLUMN IF NOT EXISTS owner_key VARCHAR(128);

ALTER TABLE mate_memory_recall ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'TEAM';

CREATE INDEX IF NOT EXISTS idx_memory_recall_scope_owner ON mate_memory_recall (agent_id, scope, owner_key);

ALTER TABLE mate_fact ADD COLUMN IF NOT EXISTS owner_key VARCHAR(128);

ALTER TABLE mate_fact ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'TEAM';

CREATE INDEX IF NOT EXISTS idx_fact_scope_owner ON mate_fact (agent_id, scope, owner_key);
