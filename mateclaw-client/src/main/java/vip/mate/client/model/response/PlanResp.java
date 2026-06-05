package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行计划
 */
@Data
public class PlanResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String agentId;
    private String goal;
    private String status;
    private Integer totalSteps;
    private Integer completedSteps;
    private String summary;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
    private List<SubPlanResp> steps;
}
