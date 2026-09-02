-- V121__tool_disclosure_tier.sql (MySQL dialect)
--
-- Mirror of the H2 V121, adapted for MySQL 8.0 — no "IF NOT EXISTS" on
-- ADD COLUMN, so we guard with INFORMATION_SCHEMA + prepared statement to
-- stay idempotent (essential for desktop installs that re-apply migrations).
--
-- See the H2 file for the column semantics.

-- 1. mate_tool.disclosure_tier (default 'core')

ALTER TABLE mate_tool ADD COLUMN IF NOT EXISTS disclosure_tier VARCHAR(16) DEFAULT 'core';

ALTER TABLE mate_mcp_server ADD COLUMN IF NOT EXISTS disclosure_tier VARCHAR(16) DEFAULT 'core';

UPDATE mate_tool
SET disclosure_tier = 'extension'
WHERE name IN ('ImageGenerateTool', 'MusicGenerateTool', 'VideoGenerateTool', 'Model3dGenerateTool', 'BrowserUseTool')
  AND (disclosure_tier IS NULL OR disclosure_tier = 'core');
