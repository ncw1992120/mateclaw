-- ============================================================
-- V218: Aloudata 指标表补充业务唯一键
-- ============================================================
-- 复核发现：dataagent_aloudata_metric 此前仅有主键（id），无业务唯一键。
-- 每次全量同步均以新雪花 ID 插入、依赖 sync_version 清理旧版本，
-- 分页抖动时可能产生同版本重复行。补充 (datasource_id, metric_name)
-- 唯一索引后，upsertBatch 按业务键冲突更新，与维度/类目/关联表行为对齐。
-- 前置：先按 sync_version、id 保留最大者清理存量重复行，否则建索引失败。
-- 注：V203 建表语句已回填该唯一索引；本迁移保留以兼容存量库，
--     CREATE UNIQUE INDEX IF NOT EXISTS 在新库上自动跳过。
-- 语句与 MySQL 版（V218__add_aloudata_metric_unique_key.sql）逐条对应，
-- 多表 DELETE 重写为 PostgreSQL 的 DELETE ... USING 形式。
-- ============================================================

-- 1. 清理存量重复行（同 datasource_id + metric_name 保留 sync_version 最大、id 最大）
DELETE FROM dataagent_aloudata_metric m
USING dataagent_aloudata_metric newer
 WHERE m.datasource_id = newer.datasource_id
   AND m.metric_name = newer.metric_name
   AND (newer.sync_version > m.sync_version
        OR (newer.sync_version = m.sync_version AND newer.id > m.id));

-- 2. 补充业务唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_metric_ds_name ON dataagent_aloudata_metric (datasource_id, metric_name);
