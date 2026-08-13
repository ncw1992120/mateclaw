-- ============================================================
-- 业务术语表：增加关联指标 / 维度引用
-- ============================================================
-- 业务术语可关联 Aloudata 指标平台的指标或维度，便于 LLM 由术语
-- 直接定位到可查询的指标/维度（metricName / dimName）。
-- 指标/维度 ID 在全量同步时会变化，因此以 JSON 快照存储引用：
-- [{"id":123,"datasourceId":1,"datasourceName":"CRM","name":"sales_amount","displayName":"销售额"}]
-- ============================================================

ALTER TABLE `dataagent_business_term`
    ADD COLUMN `related_metrics_json`    TEXT COMMENT '关联指标引用JSON（[{"id":1,"datasourceId":1,"datasourceName":"CRM","name":"sales_amount","displayName":"销售额"}]）' AFTER `related_terms`,
    ADD COLUMN `related_dimensions_json` TEXT COMMENT '关联维度引用JSON（[{"id":1,"datasourceId":1,"datasourceName":"CRM","name":"province","displayName":"省份"}]）' AFTER `related_metrics_json`;
