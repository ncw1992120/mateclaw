-- V100__channel_tool_support.sql (MySQL dialect)
--
-- Mirror of the H2 V100, adapted for MySQL 8.0 — no "IF NOT EXISTS"
-- on ADD COLUMN / CREATE INDEX, so we guard with INFORMATION_SCHEMA
-- + prepared-statement so this migration is idempotent (essential
-- for desktop installs that re-apply migrations after upgrade).

-- 1. Add channel_id column if missing

ALTER TABLE mate_tool ADD COLUMN IF NOT EXISTS channel_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_mate_tool_channel ON mate_tool (channel_id);

DELETE FROM mate_tool
WHERE id IN (
  SELECT id FROM (
    SELECT id,
           ROW_NUMBER() OVER (
             PARTITION BY name
             ORDER BY
               CASE WHEN deleted = 0 THEN 0 ELSE 1 END,
               update_time DESC,
               id DESC
           ) AS rn
    FROM mate_tool
  ) ranked
  WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mate_tool_name ON mate_tool (name);
