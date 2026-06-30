-- ============================================================
-- 审批流程记录表
-- ============================================================
-- 独立审批实例表，与资源授权表（V206）分离。
-- 审批实例记录每次发布/授权等操作的审批流转。
-- workspace_id：所属工作区 ID，资源隔离用
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_approval_record` (
    `id`              BIGINT       NOT NULL                    COMMENT '主键（雪花ID）',
    `approval_type`   VARCHAR(64)  NOT NULL                    COMMENT '审批类型：skill_publish / agent_publish / resource_grant 等',
    `resource_type`   VARCHAR(64)  NOT NULL                    COMMENT '资源类型：skill / agent / datasource 等',
    `resource_id`     BIGINT       NOT NULL                    COMMENT '资源 ID',
    `resource_name`   VARCHAR(255) NULL                        COMMENT '资源名称（冗余，便于展示）',
    `workspace_id`    BIGINT       NOT NULL    DEFAULT 1       COMMENT '所属工作区 ID',
    `requester_id`    BIGINT       NOT NULL                    COMMENT '申请人用户 ID',
    `requester_name`  VARCHAR(128) NULL                        COMMENT '申请人名称（冗余）',
    `action`          VARCHAR(32)  NOT NULL                    COMMENT '申请动作：publish / grant / delete 等',
    `payload_json`    TEXT         NULL                        COMMENT '申请负载（JSON，存储审批所需的额外信息）',
    `status`          VARCHAR(16)  NOT NULL    DEFAULT 'pending' COMMENT '状态：pending / approved / rejected / cancelled',
    `current_step`    INT          NOT NULL    DEFAULT 0       COMMENT '当前审批步骤（0=初始，多级审批时递增）',
    `approver_id`     BIGINT       NULL                        COMMENT '审批人用户 ID（最终审批者）',
    `approver_name`   VARCHAR(128) NULL                        COMMENT '审批人名称（冗余）',
    `comment`         TEXT         NULL                        COMMENT '审批意见',
    `submitted_at`    DATETIME     NOT NULL                    COMMENT '提交时间',
    `approved_at`     DATETIME     NULL                        COMMENT '审批完成时间',
    `create_time`     DATETIME     NOT NULL                    COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL                    COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL    DEFAULT 0       COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_workspace_status` (`workspace_id`, `status`, `deleted`),
    KEY `idx_requester` (`requester_id`, `status`, `deleted`),
    KEY `idx_resource` (`resource_type`, `resource_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批流程记录表';
