package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 子计划步骤响应
 */
@Data
public class SubPlanResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long planId;
    private Integer stepIndex;
    private String description;
    private String status;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
