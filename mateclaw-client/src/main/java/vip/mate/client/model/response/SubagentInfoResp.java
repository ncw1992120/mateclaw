package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 子 Agent 信息
 */
@Data
public class SubagentInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 子代理 ID */
    private String subagentId;

    /** 父会话 ID */
    private String parentConversationId;

    /** 子会话 ID */
    private String childConversationId;

    /** 父子代理 ID */
    private String parentSubagentId;

    /** 深度 */
    private int depth;

    /** Agent ID */
    private Long agentId;

    /** 目标 */
    private String goal;

    /** 启动时间戳(epoch ms) */
    private long startedAt;

    /** 状态 */
    private String status;

    /** 工具调用次数 */
    private int toolCount;

    /** 最后调用的工具名 */
    private String lastTool;

    /** 当前阶段 */
    private String currentPhase;
}
