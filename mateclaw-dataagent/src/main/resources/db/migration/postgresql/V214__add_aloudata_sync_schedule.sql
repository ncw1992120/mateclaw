-- ============================================================
-- V214: Aloudata 语义层定时同步配置
-- ============================================================
-- 数据源表增加定时同步配置字段（页面可配置：开关 + cron 表达式）。
-- 说明：本模块不再创建 shedlock 表——dataagent 通过 mateclaw-sdk 引入
-- server 的 ShedLockConfig，其 JDBC LockProvider 复用的 DataSource 所指向的
-- 库中，shedlock 表已由 server 侧（mateclaw-server V74 迁移 / DatabaseBootstrapRunner）
-- 创建，dataagent 不重复建表。
-- ============================================================

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS aloudata_sync_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS aloudata_sync_cron VARCHAR(100) DEFAULT NULL;

ALTER TABLE dataagent_datasource ADD COLUMN IF NOT EXISTS last_aloudata_sync_time TIMESTAMP DEFAULT NULL;
