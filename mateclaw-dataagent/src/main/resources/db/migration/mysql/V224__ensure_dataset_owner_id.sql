-- owner_id was added to the original MySQL V201 create script after some
-- databases had already applied V201. Add it idempotently for those histories.
-- Keep the legacy `owner` column and its data untouched; it cannot be mapped
-- safely to the numeric user ID used by owner_id.

SET @owner_id_column_count := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dataagent_dataset'
      AND COLUMN_NAME = 'owner_id'
);
SET @owner_id_sql := IF(
    @owner_id_column_count = 0,
    'ALTER TABLE `dataagent_dataset` ADD COLUMN `owner_id` BIGINT NULL DEFAULT NULL COMMENT ''所有者用户 ID''',
    'SELECT 1'
);
PREPARE owner_id_stmt FROM @owner_id_sql;
EXECUTE owner_id_stmt;
DEALLOCATE PREPARE owner_id_stmt;
