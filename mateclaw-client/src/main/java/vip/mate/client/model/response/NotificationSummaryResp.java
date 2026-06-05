package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知摘要
 */
@Data
public class NotificationSummaryResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 待审批数量 */
    private int pendingApprovals;

    /** 卡住的 Agent 数量 */
    private int stuckAgents;

    /** 失败的定时任务数 */
    private int failedCrons;

    /** 下线渠道数 */
    private int downChannels;

    /** 下线 MCP 服务数 */
    private int downMcps;
}
