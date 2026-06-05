package vip.mate.client.model.request;

import lombok.Data;
import vip.mate.client.model.DeliveryConfig;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务请求
 */
@Data
public class CronJobReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private String name;
    private String cronExpression;
    private String timezone;
    private Long agentId;
    private String taskType;
    private String triggerMessage;
    private String requestBody;
    private Boolean enabled;
    private LocalDateTime nextRunTime;
    private LocalDateTime lastRunTime;
    private Long channelId;
    private DeliveryConfig deliveryConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String lastDeliveryStatus;
    private String lastDeliveryError;
}
