-- See the H2 file for context. MySQL 8.0 doesn't support
-- `ADD COLUMN IF NOT EXISTS`, so the existence check goes through
-- INFORMATION_SCHEMA + a prepared statement.

ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS model_provider VARCHAR(64);

ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS model_name VARCHAR(128);
