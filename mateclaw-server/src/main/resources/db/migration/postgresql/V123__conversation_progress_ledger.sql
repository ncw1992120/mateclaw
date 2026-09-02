-- V100: per-conversation progress ledger (see the H2 copy for full background).
--
-- MySQL idempotency: ALTER TABLE ADD COLUMN IF NOT EXISTS is not portable
-- across server versions, so guard with INFORMATION_SCHEMA + a prepared
-- statement so re-running the migration on an already-patched schema is a
-- no-op.

ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS progress_ledger TEXT;
