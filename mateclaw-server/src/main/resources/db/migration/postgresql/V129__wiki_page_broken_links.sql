-- V129: Persisted wikilink lint state — MySQL dialect.
--
-- See h2/V129__wiki_page_broken_links.sql for column semantics. The MySQL
-- variant needs INFORMATION_SCHEMA guards because MySQL doesn't support
-- ADD COLUMN IF NOT EXISTS prior to 8.0.29 and the deploy targets older
-- supported versions.

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS broken_links JSONB DEFAULT NULL;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS broken_links_scanned_at TIMESTAMP(3) DEFAULT NULL;
