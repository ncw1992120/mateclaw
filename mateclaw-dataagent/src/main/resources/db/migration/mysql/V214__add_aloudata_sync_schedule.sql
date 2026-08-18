-- ============================================================
-- V214: Aloudata 语义层定时同步配置
-- ============================================================
-- 数据源表增加定时同步配置字段（页面可配置：开关 + cron 表达式）。
-- 说明：本模块不再创建 shedlock 表——dataagent 通过 mateclaw-sdk 引入
-- server 的 ShedLockConfig，其 JDBC LockProvider 复用的 DataSource 所指向的
-- 库中，shedlock 表已由 server 侧（mateclaw-server V74 迁移 / DatabaseBootstrapRunner）
-- 创建，dataagent 不重复建表。
-- ============================================================

ALTER TABLE `dataagent_datasource`
    ADD COLUMN `aloudata_sync_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Aloudata 语义层定时同步开关（1=开启，0=关闭）' AFTER `enabled`,
    ADD COLUMN `aloudata_sync_cron` VARCHAR(100) DEFAULT NULL COMMENT 'Aloudata 语义层定时同步 cron 表达式（5 段：分 时 日 月 周，秒固定为 0，如 0 2 * * *）' AFTER `aloudata_sync_enabled`,
    ADD COLUMN `last_aloudata_sync_time` DATETIME DEFAULT NULL COMMENT '最近一次 Aloudata 语义层同步完成时间（含手动与定时）' AFTER `aloudata_sync_cron`;
