package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全审计日志
 */
@Data
public class AuditLogResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 对话ID */
    private String conversationId;

    /** Agent ID */
    private String agentId;

    /** 用户ID */
    private String userId;

    /** 渠道类型 */
    private String channelType;

    /** 工具名称 */
    private String toolName;

    /** 工具参数(JSON) */
    private String toolParamsJson;

    /** 决策结果 */
    private String decision;

    /** 最高严重等级 */
    private String maxSeverity;

    /** 发现(JSON) */
    private String findingsJson;

    /** 待审批ID */
    private String pendingId;

    /** 重放载荷哈希 */
    private String replayPayloadHash;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
