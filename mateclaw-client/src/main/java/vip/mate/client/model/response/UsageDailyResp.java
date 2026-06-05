package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日使用统计
 */
@Data
public class UsageDailyResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private Long agentId;
    private LocalDate statDate;
    private Integer conversationCount;
    private Integer messageCount;
    private Long totalTokens;
    private Long promptTokens;
    private Long completionTokens;
    private Long cacheReadTokens;
    private Long cacheWriteTokens;
    private Integer toolCallCount;
    private Integer errorCount;
    private LocalDateTime createTime;
}
