package vip.mate.dataagent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流程记录实体
 * <p>
 * 记录每次发布/授权等操作的审批流转，与资源授权表（dataagent_resource_grant）分离。
 * 审批通过后，由业务层决定是否写入资源授权表。
 */
@Data
@TableName("dataagent_approval_record")
public class ApprovalRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 审批类型：skill_publish / agent_publish / resource_grant 等 */
    private String approvalType;

    /** 资源类型：skill / agent / datasource 等 */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 资源名称（冗余，便于展示） */
    private String resourceName;

    /** 所属工作区 ID */
    private Long workspaceId;

    /** 申请人用户 ID */
    private Long requesterId;

    /** 申请人名称（冗余） */
    private String requesterName;

    /** 申请动作：publish / grant / delete 等 */
    private String action;

    /** 申请负载（JSON，存储审批所需的额外信息） */
    private String payloadJson;

    /** 状态：pending / approved / rejected / cancelled */
    private String status;

    /** 当前审批步骤（0=初始，多级审批时递增） */
    private Integer currentStep;

    /** 审批人用户 ID（最终审批者） */
    private Long approverId;

    /** 审批人名称（冗余） */
    private String approverName;

    /** 审批意见 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String comment;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 审批完成时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
