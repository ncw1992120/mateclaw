-- V218 was already used on the dataagent base branch to create
-- dataagent_user_uid_mapping. On databases that applied that migration,
-- Flyway records V218 as successful and will not run the replacement V218
-- dataset contract migration. Reconcile the schema here without changing
-- existing values so both histories converge on the same dataset contract.

ALTER TABLE dataagent_dataset
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(40) DEFAULT 'JDBC_TABLE',
    ADD COLUMN IF NOT EXISTS source_config TEXT,
    ADD COLUMN IF NOT EXISTS schema_version INTEGER DEFAULT 1;

UPDATE dataagent_dataset
SET source_type = 'JDBC_TABLE'
WHERE source_type IS NULL OR source_type = '';

UPDATE dataagent_dataset
SET schema_version = 1
WHERE schema_version IS NULL;

ALTER TABLE dataagent_dataset
    ALTER COLUMN source_type SET DEFAULT 'JDBC_TABLE',
    ALTER COLUMN source_type SET NOT NULL,
    ALTER COLUMN schema_version SET DEFAULT 1,
    ALTER COLUMN schema_version SET NOT NULL;

COMMENT ON COLUMN dataagent_dataset.source_type IS '统一来源类型：JDBC_TABLE/JDBC_SQL/ALOUDATA_ANALYSIS_VIEW/HTTP_API/FILE';
COMMENT ON COLUMN dataagent_dataset.source_config IS '来源配置（仅 DataAgent 内部使用）';
COMMENT ON COLUMN dataagent_dataset.schema_version IS '数据集契约版本';
