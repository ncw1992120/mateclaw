-- NULL 使公共数据集及已删除记录不参与组件别名唯一约束。
ALTER TABLE dataagent_dataset
    ADD COLUMN dashboard_id BIGINT NULL,
    ADD COLUMN component_id VARCHAR(128) NULL,
    ADD COLUMN active_component_alias TINYINT GENERATED ALWAYS AS
        (CASE WHEN deleted = 0 AND component_id IS NOT NULL THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE INDEX uk_dataset_component_alias
        (workspace_id, dashboard_id, component_id, name, active_component_alias);
