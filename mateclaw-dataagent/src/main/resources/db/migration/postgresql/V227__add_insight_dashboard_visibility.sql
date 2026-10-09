-- 洞察仪表盘：增加可见性与模板元信息（样例模板能力）
-- visibility: private(默认) / workspace / template / official
-- template_meta: 模板元信息 JSON（tags / category / cover 等）

ALTER TABLE dataagent_insight_dashboard ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'private';
ALTER TABLE dataagent_insight_dashboard ADD COLUMN IF NOT EXISTS template_meta TEXT DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_insight_visibility ON dataagent_insight_dashboard (visibility);

COMMENT ON COLUMN dataagent_insight_dashboard.visibility IS '可见性：private/workspace/template/official';
COMMENT ON COLUMN dataagent_insight_dashboard.template_meta IS '模板元信息 JSON（tags/category/cover 等）';
