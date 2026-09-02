-- Page-level embedding columns. See the h2 sibling for the prose
-- explanation. MySQL lacks `ADD COLUMN IF NOT EXISTS`, so each column
-- guarded by an INFORMATION_SCHEMA check + prepared statement.

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS embedding BYTEA DEFAULT NULL;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(64) DEFAULT NULL;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS embedding_text_version VARCHAR(32) DEFAULT NULL;
