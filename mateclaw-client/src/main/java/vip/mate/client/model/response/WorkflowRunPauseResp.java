package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流暂停记录
 */
@Data
public class WorkflowRunPauseResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long runId;
    private Long stepId;
    private String pauseKind;
    private String pauseToken;
    private Long externalApprovalId;
    private LocalDateTime pausedAt;
    private LocalDateTime resumeDeadline;
    private String resumePayloadRef;
    private LocalDateTime resumedAt;
    private String resumeOutcome;
}
