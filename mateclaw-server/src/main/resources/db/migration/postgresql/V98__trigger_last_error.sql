-- See the H2 file for context. MySQL 8.0 doesn't support
-- `ADD COLUMN IF NOT EXISTS`, so the existence check goes through
-- INFORMATION_SCHEMA + a prepared statement.

ALTER TABLE mate_trigger ADD COLUMN IF NOT EXISTS last_error VARCHAR(2048);

ALTER TABLE mate_trigger ADD COLUMN IF NOT EXISTS last_dispatched_at TIMESTAMP;
