-- ============================================================
-- 审批流程记录表
-- ============================================================
-- 独立审批实例表，与资源授权表（V206）分离。
-- 审批实例记录每次发布/授权等操作的审批流转。
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

CREATE TABLE IF NOT EXISTS dataagent_approval_record (
    id BIGINT NOT NULL,
    approval_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_name VARCHAR(255),
    workspace_id BIGINT NOT NULL DEFAULT 1,
    requester_id BIGINT NOT NULL,
    requester_name VARCHAR(128),
    action VARCHAR(32) NOT NULL,
    payload_json TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    current_step INTEGER NOT NULL DEFAULT 0,
    approver_id BIGINT,
    approver_name VARCHAR(128),
    comment TEXT,
    submitted_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    deleted SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_workspace_status ON dataagent_approval_record (workspace_id, status, deleted);

CREATE INDEX IF NOT EXISTS idx_requester ON dataagent_approval_record (requester_id, status, deleted);

CREATE INDEX IF NOT EXISTS idx_resource ON dataagent_approval_record (resource_type, resource_id, deleted);

COMMENT ON TABLE dataagent_approval_record IS '审批流程记录表';

COMMENT ON COLUMN dataagent_approval_record.id IS '主键（雪花ID）';
COMMENT ON COLUMN dataagent_approval_record.approval_type IS '审批类型：skill_publish / agent_publish / resource_grant 等';
COMMENT ON COLUMN dataagent_approval_record.resource_type IS '资源类型：skill / agent / datasource 等';
COMMENT ON COLUMN dataagent_approval_record.resource_id IS '资源 ID';
COMMENT ON COLUMN dataagent_approval_record.resource_name IS '资源名称（冗余，便于展示）';
COMMENT ON COLUMN dataagent_approval_record.workspace_id IS '所属工作区 ID';
COMMENT ON COLUMN dataagent_approval_record.requester_id IS '申请人用户 ID';
COMMENT ON COLUMN dataagent_approval_record.requester_name IS '申请人名称（冗余）';
COMMENT ON COLUMN dataagent_approval_record.action IS '申请动作：publish / grant / delete 等';
COMMENT ON COLUMN dataagent_approval_record.payload_json IS '申请负载（JSON，存储审批所需的额外信息）';
COMMENT ON COLUMN dataagent_approval_record.status IS '状态：pending / approved / rejected / cancelled';
COMMENT ON COLUMN dataagent_approval_record.current_step IS '当前审批步骤（0=初始，多级审批时递增）';
COMMENT ON COLUMN dataagent_approval_record.approver_id IS '审批人用户 ID（最终审批者）';
COMMENT ON COLUMN dataagent_approval_record.approver_name IS '审批人名称（冗余）';
COMMENT ON COLUMN dataagent_approval_record.comment IS '审批意见';
COMMENT ON COLUMN dataagent_approval_record.submitted_at IS '提交时间';
COMMENT ON COLUMN dataagent_approval_record.approved_at IS '审批完成时间';
COMMENT ON COLUMN dataagent_approval_record.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_approval_record.update_time IS '更新时间';
COMMENT ON COLUMN dataagent_approval_record.deleted IS '逻辑删除：0-正常 / 1-已删除';
