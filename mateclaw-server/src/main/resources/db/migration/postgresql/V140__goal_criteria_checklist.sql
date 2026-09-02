-- V140: Structured, checkable criteria for goals (MySQL).
--
-- See the H2 counterpart for the full rationale. MySQL has no
-- "ADD COLUMN IF NOT EXISTS", so the column is guarded with an
-- INFORMATION_SCHEMA existence check + prepared statement for idempotency.
--
-- The column holds the goal's checklist as JSON:
--   [{ "id": "C1", "text": "...", "passed": false, "evidence": "" }, ...]
-- Additive and nullable, so existing goals load unchanged (a NULL list
-- bootstraps on first evaluation).

ALTER TABLE mate_agent_goal ADD COLUMN IF NOT EXISTS criteria JSONB;
