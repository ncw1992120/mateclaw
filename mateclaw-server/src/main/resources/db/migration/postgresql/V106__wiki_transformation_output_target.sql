-- Two-part follow-up to V105 so a transformation's output can flow back
-- into the KB as a first-class artifact. See the h2 sibling migration for
-- the prose explanation. MySQL lacks `ADD COLUMN IF NOT EXISTS`, so each
-- column is guarded by an INFORMATION_SCHEMA check + prepared statement.

ALTER TABLE mate_wiki_transformation ADD COLUMN IF NOT EXISTS output_target VARCHAR(16) NOT NULL DEFAULT 'none';

ALTER TABLE mate_wiki_transformation_run ADD COLUMN IF NOT EXISTS output_page_id BIGINT;
