-- V66: Per-model capability declaration (issue #44)
-- MySQL lacks ADD COLUMN IF NOT EXISTS; use INFORMATION_SCHEMA guard instead.

ALTER TABLE mate_model_config ADD COLUMN IF NOT EXISTS modalities VARCHAR(512) DEFAULT NULL;
