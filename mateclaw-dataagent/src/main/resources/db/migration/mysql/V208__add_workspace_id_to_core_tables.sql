-- ============================================================
-- V208: 为 dataagent 核心资源表追加 workspace_id 列
-- ============================================================
-- 阶段2 Agent权限隔离：所有核心资源按 workspace 隔离
-- 默认值 1（对应 mateclaw-server 的默认工作区 default）
-- ============================================================

-- 数据源表
ALTER TABLE dataagent_datasource
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- 数据集表
ALTER TABLE dataagent_dataset
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- 业务术语表
ALTER TABLE dataagent_business_term
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- 语义模型表
ALTER TABLE dataagent_semantic_model
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- 逻辑外键关系表
ALTER TABLE dataagent_logical_relation
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- Aloudata 指标表
ALTER TABLE dataagent_aloudata_metric
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- Aloudata 维度表
ALTER TABLE dataagent_aloudata_dimension
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- Aloudata 类目表
ALTER TABLE dataagent_aloudata_category
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);

-- 帮助文档表
ALTER TABLE dataagent_help_document
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属工作区 ID' AFTER id,
    ADD INDEX idx_workspace_id (workspace_id);
