-- ============================================================
-- 通用资源授权表
-- ============================================================
-- 通过一张表管理所有资源的授权关系，避免每种资源一张授权表的膨胀。
-- 支持 skill 授权、发布审批等场景的权限定义。
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

CREATE TABLE IF NOT EXISTS dataagent_resource_grant (
    id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    grant_type VARCHAR(32) NOT NULL,
    grantee_id VARCHAR(128) NOT NULL,
    permission VARCHAR(32) NOT NULL DEFAULT 'use',
    granted_by BIGINT,
    status SMALLINT NOT NULL DEFAULT 1,
    expire_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_grant ON dataagent_resource_grant (resource_type, resource_id, grant_type, grantee_id, permission, deleted);

CREATE INDEX IF NOT EXISTS idx_workspace_resource ON dataagent_resource_grant (workspace_id, resource_type, resource_id);

CREATE INDEX IF NOT EXISTS idx_grantee ON dataagent_resource_grant (grant_type, grantee_id, status);

COMMENT ON TABLE dataagent_resource_grant IS '通用资源授权表';

COMMENT ON COLUMN dataagent_resource_grant.id IS '主键（雪花ID）';
COMMENT ON COLUMN dataagent_resource_grant.resource_type IS '资源类型：skill / agent / datasource / business_term 等';
COMMENT ON COLUMN dataagent_resource_grant.resource_id IS '资源 ID（对应业务表的主键）';
COMMENT ON COLUMN dataagent_resource_grant.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_resource_grant.grant_type IS '授权类型：role / user / group（按角色/用户/用户组授权）';
COMMENT ON COLUMN dataagent_resource_grant.grantee_id IS '被授权者标识：角色名/用户ID/用户组ID';
COMMENT ON COLUMN dataagent_resource_grant.permission IS '权限：view / use / edit（查看/使用/编辑）';
COMMENT ON COLUMN dataagent_resource_grant.granted_by IS '授权人用户 ID';
COMMENT ON COLUMN dataagent_resource_grant.status IS '状态：0-已撤销 / 1-生效中';
COMMENT ON COLUMN dataagent_resource_grant.expire_time IS '过期时间（NULL 表示永久）';
COMMENT ON COLUMN dataagent_resource_grant.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_resource_grant.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_resource_grant.deleted IS '逻辑删除：0-正常 / 1-已删除';
