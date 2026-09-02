-- See the H2 file for context. MySQL 8.0 supports neither
-- `ADD COLUMN IF NOT EXISTS` nor `CREATE INDEX IF NOT EXISTS`, so each
-- column and index is guarded by an INFORMATION_SCHEMA check applied via a
-- prepared statement (matches the pattern in V113 / V116).
--
-- Column types: archived_at / last_activity_at use DATETIME(3) to match
-- mate_skill_usage_stat.last_loaded_at; lifecycle_state VARCHAR(16);
-- pinned TINYINT(1).

ALTER TABLE mate_skill ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(16) DEFAULT 'active';

ALTER TABLE mate_skill ADD COLUMN IF NOT EXISTS pinned BOOLEAN DEFAULT FALSE;

ALTER TABLE mate_skill ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP(3);

ALTER TABLE mate_skill ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP(3);

CREATE INDEX IF NOT EXISTS idx_skill_lifecycle_state ON mate_skill (lifecycle_state);

CREATE INDEX IF NOT EXISTS idx_skill_last_activity_at ON mate_skill (last_activity_at);

UPDATE mate_skill SET last_activity_at = (
  SELECT MAX(last_loaded_at) FROM mate_skill_usage_stat s
   WHERE s.skill_name = mate_skill.name
)
WHERE last_activity_at IS NULL;

UPDATE mate_skill SET lifecycle_state = 'active' WHERE lifecycle_state IS NULL;
