-- V218 was already used on the dataagent base branch to create
-- dataagent_user_uid_mapping. On databases that applied that migration,
-- Flyway records V218 as successful and will not run the replacement V218
-- dataset contract migration. Reconcile the schema here without changing
-- existing values so both histories converge on the same dataset contract.

SET @source_type_column_count := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dataagent_dataset'
      AND COLUMN_NAME = 'source_type'
);
SET @source_type_sql := IF(
    @source_type_column_count = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `source_type` VARCHAR(40) NOT NULL DEFAULT ''JDBC_TABLE'' COMMENT ''统一来源类型：JDBC_TABLE/JDBC_SQL/ALOUDATA_ANALYSIS_VIEW/HTTP_API/FILE''',
    'SELECT 1'
);
PREPARE source_type_stmt FROM @source_type_sql;
EXECUTE source_type_stmt;
DEALLOCATE PREPARE source_type_stmt;

SET @source_config_column_count := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dataagent_dataset'
      AND COLUMN_NAME = 'source_config'
);
SET @source_config_sql := IF(
    @source_config_column_count = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `source_config` TEXT DEFAULT NULL COMMENT ''来源配置（仅 DataAgent 内部使用）''',
    'SELECT 1'
);
PREPARE source_config_stmt FROM @source_config_sql;
EXECUTE source_config_stmt;
DEALLOCATE PREPARE source_config_stmt;

SET @schema_version_column_count := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dataagent_dataset'
      AND COLUMN_NAME = 'schema_version'
);
SET @schema_version_sql := IF(
    @schema_version_column_count = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `schema_version` INT NOT NULL DEFAULT 1 COMMENT ''数据集契约版本''',
    'SELECT 1'
);
PREPARE schema_version_stmt FROM @schema_version_sql;
EXECUTE schema_version_stmt;
DEALLOCATE PREPARE schema_version_stmt;

UPDATE `dataagent_dataset`
SET `source_type` = 'JDBC_TABLE'
WHERE `source_type` IS NULL OR `source_type` = '';

UPDATE `dataagent_dataset`
SET `schema_version` = 1
WHERE `schema_version` IS NULL;
