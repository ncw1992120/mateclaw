package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 活跃的定时任务执行
 */
@Data
public class ActiveCronRunResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long runId;
    private Long jobId;
    private String jobName;
    private String triggerType;
    private String conversationId;
    private LocalDateTime startedAt;
}
