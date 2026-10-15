-- Ensure old databases that already applied V201 have the owner_id column.
-- Existing rows remain NULL and keep their historical ownership behavior.

ALTER TABLE dataagent_dataset
    ADD COLUMN IF NOT EXISTS owner_id BIGINT DEFAULT NULL;

COMMENT ON COLUMN dataagent_dataset.owner_id IS '所有者用户 ID';
