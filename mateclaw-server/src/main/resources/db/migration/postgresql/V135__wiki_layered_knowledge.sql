-- V135: Layered knowledge (fact / experience) + page dependency graph.
-- See the H2 file for rationale. MySQL uses INFORMATION_SCHEMA guards for the
-- idempotent column adds.

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS knowledge_layer VARCHAR(16);

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS depends_on_json TEXT;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS stale SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE mate_wiki_page ADD COLUMN IF NOT EXISTS stale_reason_json TEXT;

CREATE TABLE IF NOT EXISTS mate_wiki_page_dependency (
    id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    depends_on_page_id BIGINT NOT NULL,
    dependency_type VARCHAR(32) NOT NULL DEFAULT 'fact',
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wiki_page_dep ON mate_wiki_page_dependency (page_id, depends_on_page_id, dependency_type, deleted);

CREATE INDEX IF NOT EXISTS idx_wiki_page_dep_reverse ON mate_wiki_page_dependency (kb_id, depends_on_page_id, deleted);
