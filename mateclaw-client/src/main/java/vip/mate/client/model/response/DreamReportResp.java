package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Dream 报告
 */
@Data
public class DreamReportResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long agentId;
    private String mode;
    private String topic;
    private String triggerSource;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer candidateCount;
    private Integer promotedCount;
    private Integer rejectedCount;
    private String memoryDiff;
    private String llmReason;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
