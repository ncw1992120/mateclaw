package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 目标实体
 */
@Data
public class GoalResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String conversationId;
    private Long agentId;
    private Long workspaceId;
    private String createdBy;
    private String title;
    private String description;
    private String exitCriteria;
    private String successCheckPrompt;
    private String status;
    private Integer turnBudget;
    private Integer turnsUsed;
    private Integer llmCallBudget;
    private Integer agentLlmCallsUsed;
    private Integer evalLlmCallsUsed;
    private String progressSummary;
    private Double completionScore;
    private LocalDateTime lastEvaluationAt;
    private Boolean autoFollowupEnabled;
    private Integer followupCooldownSeconds;
    private LocalDateTime lastFollowupAt;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
