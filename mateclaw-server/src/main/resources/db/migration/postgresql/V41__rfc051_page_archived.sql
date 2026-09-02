-- V41: RFC-051 PR-7 — soft-archive flag.

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS archived SMALLINT NOT NULL DEFAULT 0;
