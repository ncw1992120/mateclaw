package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具审批记录
 */
@Data
public class ToolApprovalResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String pendingId;
    private String conversationId;
    private String userId;
    private String agentId;
    private String channelType;
    private String requesterName;
    private String replyTarget;
    private String toolName;
    private String toolArguments;
    private String toolCallPayload;
    private String toolCallHash;
    private String siblingToolCalls;
    private String summary;
    private String findingsJson;
    private String maxSeverity;
    private String status;
    private String resolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime expireAt;
    private String chatOrigin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
