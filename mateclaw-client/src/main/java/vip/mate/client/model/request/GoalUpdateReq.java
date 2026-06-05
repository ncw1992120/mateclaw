package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新目标请求
 */
@Data
public class GoalUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String description;
    private String exitCriteria;
    private String successCheckPrompt;
    private Integer turnBudget;
    private Integer llmCallBudget;
    private Boolean autoFollowupEnabled;
    private Integer followupCooldownSeconds;
}
