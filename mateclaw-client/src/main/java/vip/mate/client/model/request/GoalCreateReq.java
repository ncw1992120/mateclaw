package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建目标请求
 */
@Data
public class GoalCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String conversationId;
    private Long agentId;
    private Long workspaceId;
    private String title;
    private String description;
    private String exitCriteria;
    private String successCheckPrompt;
    private Integer turnBudget;
    private Integer llmCallBudget;
    private Boolean autoFollowupEnabled;
    private Integer followupCooldownSeconds;
}
