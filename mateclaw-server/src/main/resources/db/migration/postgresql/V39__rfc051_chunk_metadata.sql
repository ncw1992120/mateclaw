-- V39: RFC-051 PR-1a — chunk structural metadata.
-- MySQL lacks `ADD COLUMN IF NOT EXISTS`; use INFORMATION_SCHEMA guard instead.

ALTER TABLE mate_wiki_chunk ADD COLUMN IF NOT EXISTS page_number INTEGER DEFAULT NULL;

ALTER TABLE mate_wiki_chunk ADD COLUMN IF NOT EXISTS token_count INTEGER DEFAULT NULL;

ALTER TABLE mate_wiki_chunk ADD COLUMN IF NOT EXISTS header_breadcrumb VARCHAR(1024) DEFAULT NULL;

ALTER TABLE mate_wiki_chunk ADD COLUMN IF NOT EXISTS source_section VARCHAR(512) DEFAULT NULL;
