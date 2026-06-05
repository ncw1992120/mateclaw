package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent 运行时任务卡片
 */
@Data
public class RunCardResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private String conversationId;

    /** Agent ID */
    private Long agentId;

    /** Agent 名称 */
    private String agentName;

    /** Agent 图标 */
    private String agentIcon;

    /** 用户名 */
    private String username;

    /** 当前阶段 */
    private String currentPhase;

    /** 正在运行的工具名 */
    private String runningToolName;

    /** 等待原因 */
    private String waitingReason;

    /** 是否完成 */
    private boolean done;

    /** 是否请求停止 */
    private boolean stopRequested;

    /** 是否已收到第一个 token */
    private boolean firstTokenReceived;

    /** 订阅者数量 */
    private int subscriberCount;

    /** 队列长度 */
    private int queueLen;

    /** 运行时长(毫秒) */
    private long ageMs;

    /** 距上次事件的毫秒数 */
    private long msSinceLastEvent;

    /** 卡住原因 */
    private String stuckReason;

    /** 是否孤儿(无订阅者) */
    private boolean orphan;

    /** 子 Agent 数量 */
    private int subagentCount;
}
