package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 审批处理请求 DTO
 */
@Data
public class ApprovalProcessRequest {

    /** 审批意见 */
    private String comment;
}
