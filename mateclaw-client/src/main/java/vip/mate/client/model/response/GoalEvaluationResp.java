package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 目标评估结果
 */
@Data
public class GoalEvaluationResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private double score;
    private String gap;
    private String decision;
    private boolean completed;
    private String evaluatorModel;
    private int llmCallsConsumed;
    private long latencyMs;
}
