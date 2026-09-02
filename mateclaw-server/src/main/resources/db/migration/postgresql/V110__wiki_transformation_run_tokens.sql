-- Record per-run token usage. See h2 sibling for prose explanation.

ALTER TABLE mate_wiki_transformation_run ADD COLUMN IF NOT EXISTS input_tokens BIGINT;

ALTER TABLE mate_wiki_transformation_run ADD COLUMN IF NOT EXISTS output_tokens BIGINT;

ALTER TABLE mate_wiki_transformation_run ADD COLUMN IF NOT EXISTS total_tokens BIGINT;
