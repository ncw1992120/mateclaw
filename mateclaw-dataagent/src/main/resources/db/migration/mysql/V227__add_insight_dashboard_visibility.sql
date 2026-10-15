-- 洞察仪表盘：增加可见性与模板元信息（样例模板能力）
-- visibility: private(默认) / workspace / template / official
-- template_meta: 模板元信息 JSON（tags / category / cover 等）

SET @v227_visibility_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_insight_dashboard' AND COLUMN_NAME = 'visibility'
);
SET @v227_visibility_ddl := IF(@v227_visibility_exists = 0,
    'ALTER TABLE `dataagent_insight_dashboard` ADD COLUMN `visibility` VARCHAR(32) NOT NULL DEFAULT ''private'' COMMENT ''可见性：private/workspace/template/official''',
    'SELECT 1'
);
PREPARE v227_visibility_stmt FROM @v227_visibility_ddl;
EXECUTE v227_visibility_stmt;
DEALLOCATE PREPARE v227_visibility_stmt;

SET @v227_template_meta_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_insight_dashboard' AND COLUMN_NAME = 'template_meta'
);
SET @v227_template_meta_ddl := IF(@v227_template_meta_exists = 0,
    'ALTER TABLE `dataagent_insight_dashboard` ADD COLUMN `template_meta` TEXT DEFAULT NULL COMMENT ''模板元信息 JSON（tags/category/cover 等）''',
    'SELECT 1'
);
PREPARE v227_template_meta_stmt FROM @v227_template_meta_ddl;
EXECUTE v227_template_meta_stmt;
DEALLOCATE PREPARE v227_template_meta_stmt;

SET @v227_idx_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dataagent_insight_dashboard' AND INDEX_NAME = 'idx_insight_visibility'
);
SET @v227_idx_ddl := IF(@v227_idx_exists = 0,
    'CREATE INDEX idx_insight_visibility ON dataagent_insight_dashboard (visibility)',
    'SELECT 1'
);
PREPARE v227_idx_stmt FROM @v227_idx_ddl;
EXECUTE v227_idx_stmt;
DEALLOCATE PREPARE v227_idx_stmt;
