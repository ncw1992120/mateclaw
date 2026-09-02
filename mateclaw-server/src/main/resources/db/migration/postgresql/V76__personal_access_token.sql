-- V76: Personal Access Token (RFC-03 Lane I1).
-- Lets headless / CI / SDK callers authenticate without going through
-- the interactive JWT login flow. Tokens are stored as SHA-256 hashes —
-- a DB compromise reveals which user owns which token but never the
-- plaintext value the user sees once at creation time.
--
-- token_hash is the lookup key (UNIQUE) so the auth filter can do a
-- single indexed query on every authenticated request.

CREATE TABLE IF NOT EXISTS mate_personal_access_token (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(64),
    token_hash CHAR(64) NOT NULL,
    scopes VARCHAR(255),
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    enabled BOOLEAN,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pat_token_hash ON mate_personal_access_token (token_hash);

CREATE INDEX IF NOT EXISTS idx_pat_user_id ON mate_personal_access_token (user_id);

DROP TRIGGER IF EXISTS trg_mate_personal_access_token_upd_ts ON mate_personal_access_token;
CREATE TRIGGER trg_mate_personal_access_token_upd_ts BEFORE UPDATE ON mate_personal_access_token
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
