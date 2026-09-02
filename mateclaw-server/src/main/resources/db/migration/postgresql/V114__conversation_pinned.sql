-- Per-conversation pin flag. See the h2 sibling for the rationale.
-- MySQL has no ADD COLUMN IF NOT EXISTS; guard via INFORMATION_SCHEMA.

ALTER TABLE mate_conversation ADD COLUMN IF NOT EXISTS pinned INTEGER DEFAULT 0;
