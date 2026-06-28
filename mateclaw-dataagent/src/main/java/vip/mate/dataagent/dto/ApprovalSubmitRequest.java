package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 审批提交请求 DTO
 */
@Data
public class ApprovalSubmitRequest {

    /** 审批类型 */
    private String approvalType;

    /** 资源类型 */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 资源名称（便于展示） */
    private String resourceName;

    /** 申请动作：publish / grant / delete 等 */
    private String action;

    /** 申请负载（JSON 字符串） */
    private String payloadJson;
}
