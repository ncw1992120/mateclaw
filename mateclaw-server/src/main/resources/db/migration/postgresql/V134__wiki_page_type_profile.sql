-- V134: KB-scoped pageType profile + structured page metadata columns.
-- See the H2 file for the design rationale. MySQL 8 uses a VIRTUAL generated
-- column for the "one enabled profile per KB" constraint, and an
-- INFORMATION_SCHEMA guard for each idempotent ADD COLUMN (MySQL has no
-- ADD COLUMN IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS mate_wiki_page_type_profile (
    id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    config_json TEXT NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    enabled_kb    BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN enabled = 1 AND deleted = 0 THEN kb_id ELSE NULL END
        ) STORED,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wiki_ptprofile_name ON mate_wiki_page_type_profile (kb_id, name, deleted);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wiki_ptprofile_enabled ON mate_wiki_page_type_profile (enabled_kb);

CREATE INDEX IF NOT EXISTS idx_wiki_ptprofile_kb ON mate_wiki_page_type_profile (kb_id, enabled, deleted);

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS metadata_json TEXT;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS metadata_validation_status VARCHAR(32);

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS metadata_validation_json TEXT;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS template_key VARCHAR(128);

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS profile_version INTEGER;
