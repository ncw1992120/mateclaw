-- ============================================================
-- DataAgent 帮助中心功能：帮助文档分类表 + 帮助文档表 + 反馈表
-- ============================================================
-- workspace_id：所属工作区 ID，资源隔离用（仅帮助文档表需要）
-- ============================================================

-- 1. 帮助文档分类表

CREATE TABLE IF NOT EXISTS dataagent_help_category (
    id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    icon VARCHAR(100) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_help_category_parent ON dataagent_help_category (parent_id);

COMMENT ON TABLE dataagent_help_category IS '帮助文档分类表';

COMMENT ON COLUMN dataagent_help_category.id IS '主键 ID';
COMMENT ON COLUMN dataagent_help_category.name IS '分类名称';
COMMENT ON COLUMN dataagent_help_category.parent_id IS '父分类 ID（0 表示顶级分类）';
COMMENT ON COLUMN dataagent_help_category.sort_order IS '排序序号（升序）';
COMMENT ON COLUMN dataagent_help_category.icon IS '分类图标（emoji 或 URL）';
COMMENT ON COLUMN dataagent_help_category.description IS '分类描述';
COMMENT ON COLUMN dataagent_help_category.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_help_category.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_help_category.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_help_category_upd_ts ON dataagent_help_category;
CREATE TRIGGER trg_dataagent_help_category_upd_ts BEFORE UPDATE ON dataagent_help_category
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS dataagent_help_document (
    id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT DEFAULT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    sort_order INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    author VARCHAR(100) DEFAULT NULL,
    tags VARCHAR(500) DEFAULT NULL,
    view_count INTEGER DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_help_doc_workspace_id ON dataagent_help_document (workspace_id);

CREATE INDEX IF NOT EXISTS idx_help_doc_category ON dataagent_help_document (category_id);

CREATE INDEX IF NOT EXISTS idx_help_doc_status ON dataagent_help_document (status);

COMMENT ON TABLE dataagent_help_document IS '帮助文档表';

COMMENT ON COLUMN dataagent_help_document.id IS '主键 ID';
COMMENT ON COLUMN dataagent_help_document.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_help_document.category_id IS '所属分类 ID';
COMMENT ON COLUMN dataagent_help_document.title IS '文档标题';
COMMENT ON COLUMN dataagent_help_document.content IS '文档内容（Markdown 格式）';
COMMENT ON COLUMN dataagent_help_document.summary IS '文档摘要';
COMMENT ON COLUMN dataagent_help_document.sort_order IS '排序序号（升序）';
COMMENT ON COLUMN dataagent_help_document.status IS '文档状态：draft/published';
COMMENT ON COLUMN dataagent_help_document.author IS '作者';
COMMENT ON COLUMN dataagent_help_document.tags IS '标签（逗号分隔）';
COMMENT ON COLUMN dataagent_help_document.view_count IS '浏览次数';
COMMENT ON COLUMN dataagent_help_document.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_help_document.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_help_document.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_help_document_upd_ts ON dataagent_help_document;
CREATE TRIGGER trg_dataagent_help_document_upd_ts BEFORE UPDATE ON dataagent_help_document
    FOR EACH ROW EXECUTE FUNCTION set_update_time();

CREATE TABLE IF NOT EXISTS dataagent_help_feedback (
    id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    rating INTEGER DEFAULT NULL,
    suggestion VARCHAR(1000) DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_help_feedback_document ON dataagent_help_feedback (document_id);

COMMENT ON TABLE dataagent_help_feedback IS '帮助文档反馈表';

COMMENT ON COLUMN dataagent_help_feedback.id IS '主键 ID';
COMMENT ON COLUMN dataagent_help_feedback.document_id IS '文档 ID';
COMMENT ON COLUMN dataagent_help_feedback.rating IS '评分（1-5）';
COMMENT ON COLUMN dataagent_help_feedback.suggestion IS '改进建议';
COMMENT ON COLUMN dataagent_help_feedback.user_id IS '用户 ID';
COMMENT ON COLUMN dataagent_help_feedback.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_help_feedback.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_help_feedback.deleted IS '逻辑删除标记';

DROP TRIGGER IF EXISTS trg_dataagent_help_feedback_upd_ts ON dataagent_help_feedback;
CREATE TRIGGER trg_dataagent_help_feedback_upd_ts BEFORE UPDATE ON dataagent_help_feedback
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
