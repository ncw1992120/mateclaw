package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流执行实例
 */
@Data
public class WorkflowRunResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workflowId;
    private Long revisionId;
    private Long workspaceId;
    private String state;
    private String triggeredBy;
    private String triggeredMeta;
    private String initialInputRef;
    private String finalOutputRef;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private Integer deleted;
}
