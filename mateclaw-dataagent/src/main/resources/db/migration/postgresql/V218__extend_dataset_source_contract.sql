ALTER TABLE dataagent_dataset
    ADD COLUMN source_type VARCHAR(40) NOT NULL DEFAULT 'JDBC_TABLE',
    ADD COLUMN source_config TEXT,
    ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN dataagent_dataset.source_type IS '统一来源类型：JDBC_TABLE/JDBC_SQL/ALOUDATA_ANALYSIS_VIEW/HTTP_API/FILE';
COMMENT ON COLUMN dataagent_dataset.source_config IS '来源配置（仅 DataAgent 内部使用）';
COMMENT ON COLUMN dataagent_dataset.schema_version IS '数据集契约版本';

UPDATE dataagent_dataset
SET source_type = 'JDBC_TABLE'
WHERE source_type IS NULL OR source_type = '';
