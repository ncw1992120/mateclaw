-- V4: Add default_thinking_level to mate_agent
-- Supports: off / low / medium / high / max (null = follow model default)
-- MySQL lacks `ADD COLUMN IF NOT EXISTS`; use INFORMATION_SCHEMA guard instead.

ALTER TABLE mate_agent ADD COLUMN IF NOT EXISTS default_thinking_level VARCHAR(32) DEFAULT NULL;
