ALTER TABLE `dataagent_dataset`
    ADD COLUMN `source_type` VARCHAR(40) NOT NULL DEFAULT 'JDBC_TABLE'
        COMMENT '统一来源类型：JDBC_TABLE/JDBC_SQL/ALOUDATA_ANALYSIS_VIEW/HTTP_API/FILE',
    ADD COLUMN `source_config` TEXT DEFAULT NULL
        COMMENT '来源配置（仅 DataAgent 内部使用）',
    ADD COLUMN `schema_version` INT NOT NULL DEFAULT 1
        COMMENT '数据集契约版本';

UPDATE `dataagent_dataset`
SET `source_type` = 'JDBC_TABLE'
WHERE `source_type` IS NULL OR `source_type` = '';
