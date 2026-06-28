-- ============================================================
-- V209: 通用资源授权表
-- ============================================================
-- 阶段3 通用资源授权：通过一张表管理所有资源的授权关系
-- 支持 skill 授权、发布审批等场景的权限定义
-- ============================================================

CREATE TABLE IF NOT EXISTS dataagent_resource_grant (
    id              BIGINT       NOT NULL                    COMMENT '主键（雪花ID）',
    resource_type   VARCHAR(64)  NOT NULL                    COMMENT '资源类型：skill / agent / datasource / semantic_model 等',
    resource_id     BIGINT       NOT NULL                    COMMENT '资源 ID（对应业务表的主键）',
    workspace_id    BIGINT       NOT NULL    DEFAULT 1       COMMENT '所属工作区 ID',
    grant_type      VARCHAR(32)  NOT NULL                    COMMENT '授权类型：role / user / group（按角色/用户/用户组授权）',
    grantee_id      VARCHAR(128) NOT NULL                    COMMENT '被授权者标识：角色名/用户ID/用户组ID',
    permission      VARCHAR(32)  NOT NULL    DEFAULT 'use'   COMMENT '权限：use / manage / publish（使用/管理/发布）',
    granted_by      BIGINT       NULL                        COMMENT '授权人用户 ID',
    status          TINYINT      NOT NULL    DEFAULT 1       COMMENT '状态：0-已撤销 / 1-生效中',
    expire_time     DATETIME     NULL                        COMMENT '过期时间（NULL 表示永久）',
    create_time     DATETIME     NOT NULL                    COMMENT '创建时间',
    update_time     DATETIME     NOT NULL                    COMMENT '更新时间',
    deleted         TINYINT      NOT NULL    DEFAULT 0       COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_grant (resource_type, resource_id, grant_type, grantee_id, permission, deleted),
    INDEX idx_workspace_resource (workspace_id, resource_type, resource_id),
    INDEX idx_grantee (grant_type, grantee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用资源授权表';
