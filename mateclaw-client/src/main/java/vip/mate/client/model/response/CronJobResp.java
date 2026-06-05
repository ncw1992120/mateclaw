package vip.mate.client.model.response;

import lombok.Data;
import vip.mate.client.model.DeliveryConfig;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务 DTO（包含关联名称）
 */
@Data
public class CronJobResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private String name;
    private String cronExpression;
    private String timezone;
    private Long agentId;
    private String agentName;
    private String taskType;
    private String triggerMessage;
    private String requestBody;
    private Boolean enabled;
    private LocalDateTime nextRunTime;
    private LocalDateTime lastRunTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long channelId;
    private String channelName;
    private DeliveryConfig deliveryConfig;
    private String lastDeliveryStatus;
    private String lastDeliveryError;
}
