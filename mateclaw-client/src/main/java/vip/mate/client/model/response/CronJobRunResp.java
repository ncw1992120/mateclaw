package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务执行记录
 */
@Data
public class CronJobRunResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long cronJobId;
    private String conversationId;
    private String status;
    private String triggerType;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private Integer tokenUsage;
    private String deliveryStatus;
    private String deliveryTarget;
    private String deliveryError;
    private LocalDateTime createTime;
}
