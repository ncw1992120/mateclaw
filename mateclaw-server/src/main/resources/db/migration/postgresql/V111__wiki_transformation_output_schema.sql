-- Optional JSON Schema column. See h2 sibling for the prose explanation.

ALTER TABLE mate_wiki_transformation ADD COLUMN IF NOT EXISTS output_schema TEXT DEFAULT NULL;
