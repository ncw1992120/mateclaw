package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 运行时子 Agent 卡片
 */
@Data
public class SubagentCardResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 子 Agent ID */
    private String subagentId;

    /** 父会话 ID */
    private String parentConversationId;

    /** 子会话 ID */
    private String childConversationId;

    /** 根会话 ID */
    private String rootConversationId;

    /** 父子 Agent ID */
    private String parentSubagentId;

    /** 深度 */
    private int depth;

    /** Agent ID */
    private Long agentId;

    /** Agent 名称 */
    private String agentName;

    /** Agent 图标 */
    private String agentIcon;

    /** 目标 */
    private String goal;

    /** 状态 */
    private String status;

    /** 当前阶段 */
    private String currentPhase;

    /** 最后执行的工具 */
    private String lastTool;

    /** 工具调用次数 */
    private int toolCount;

    /** 运行时长(毫秒) */
    private long ageMs;
}
