-- V55 (RFC-074): explicit user-enabled flag on providers. See H2 sibling for
-- the full rationale; this file only differs in dialect-specific syntax.
--
-- PostgreSQL natively supports `ADD COLUMN IF NOT EXISTS` /
-- `CREATE INDEX IF NOT EXISTS`, so no INFORMATION_SCHEMA guard is needed;
-- date arithmetic uses `NOW() - INTERVAL '30 days'`.

-- ── Add `enabled` column ───────────────────────────────────────────────────

ALTER TABLE mate_model_provider ADD COLUMN IF NOT EXISTS enabled BOOLEAN;

CREATE INDEX IF NOT EXISTS idx_message_runtime_provider_time ON mate_message (runtime_provider, create_time);

UPDATE mate_model_provider
   SET enabled = TRUE
 WHERE api_key IS NOT NULL AND api_key <> '' AND strpos(api_key, '*') = 0;

UPDATE mate_model_provider
   SET enabled = TRUE
 WHERE oauth_access_token IS NOT NULL AND oauth_access_token <> '';

UPDATE mate_model_provider
   SET enabled = TRUE
 WHERE is_local = TRUE
   AND provider_id IN (
     SELECT DISTINCT runtime_provider
       FROM mate_message
      WHERE runtime_provider IS NOT NULL
        AND create_time >= NOW() - INTERVAL '30 days'
   );

UPDATE mate_model_provider
   SET enabled = TRUE
 WHERE provider_id IN (SELECT provider FROM mate_model_config WHERE is_default = TRUE);
