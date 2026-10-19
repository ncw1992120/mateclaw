-- 组件输入按持久化仪表盘及组件 ID 隔离；旧公共数据集保留空归属。
ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS dashboard_id BIGINT;
ALTER TABLE dataagent_dataset ADD COLUMN IF NOT EXISTS component_id VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dataset_component_alias
    ON dataagent_dataset (workspace_id, dashboard_id, component_id, name)
    WHERE deleted = 0 AND component_id IS NOT NULL;
