package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Dashboard 统计项
 */
@Data
public class DashboardStatsResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 对话数 */
    private long conversations;

    /** 消息数 */
    private long messages;

    /** 总 Token 数 */
    private long totalTokens;

    /** 输入 Token 数 */
    private long promptTokens;

    /** 输出 Token 数 */
    private long completionTokens;

    /** 工具调用次数 */
    private long toolCalls;
}
