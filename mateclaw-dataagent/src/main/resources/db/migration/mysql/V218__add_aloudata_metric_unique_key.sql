-- ============================================================
-- V218: Aloudata 指标表补充业务唯一键
-- ============================================================
-- 复核发现：dataagent_aloudata_metric 此前仅有主键（id），无业务唯一键。
-- 每次全量同步均以新雪花 ID 插入、依赖 sync_version 清理旧版本，
-- 分页抖动时可能产生同版本重复行。补充 (datasource_id, metric_name)
-- 唯一键后，upsertBatch 按业务键冲突更新，与维度/类目/关联表行为对齐。
-- 前置：先按 sync_version、id 保留最大者清理存量重复行，否则加键失败。
-- 注：V203 建表语句已回填该唯一键；本迁移保留以兼容存量库，
--     MySQL 无 ADD UNIQUE KEY IF NOT EXISTS，用 information_schema 判重跳过。
-- ============================================================

-- 1. 清理存量重复行（同 datasource_id + metric_name 保留 sync_version 最大、id 最大）
DELETE m FROM `dataagent_aloudata_metric` m
JOIN `dataagent_aloudata_metric` newer
  ON m.`datasource_id` = newer.`datasource_id`
 AND m.`metric_name` = newer.`metric_name`
 AND (newer.`sync_version` > m.`sync_version`
      OR (newer.`sync_version` = m.`sync_version` AND newer.`id` > m.`id`));

-- 2. 补充业务唯一键（已存在则跳过）
SET @uk_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'dataagent_aloudata_metric'
      AND INDEX_NAME = 'uk_metric_ds_name'
);
SET @ddl := IF(@uk_exists = 0,
    'ALTER TABLE `dataagent_aloudata_metric` ADD UNIQUE KEY `uk_metric_ds_name` (`datasource_id`, `metric_name`)',
    'SELECT ''uk_metric_ds_name already exists'' AS note');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
