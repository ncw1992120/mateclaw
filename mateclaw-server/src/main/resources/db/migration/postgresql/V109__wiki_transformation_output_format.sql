-- Output format declared on the template. See h2 sibling for the prose
-- explanation. MySQL needs the INFORMATION_SCHEMA guard pattern.

ALTER TABLE mate_wiki_transformation ADD COLUMN IF NOT EXISTS output_format VARCHAR(16) NOT NULL DEFAULT 'markdown';
