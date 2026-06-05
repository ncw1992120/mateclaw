package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Wiki 转换执行记录
 */
@Data
public class WikiTransformationRunResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long transformationId;
    private Long kbId;
    private Long workspaceId;
    private String inputKind;
    private Long rawId;
    private Long pageId;
    private String status;
    private String output;
    private String error;
    private Long modelId;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private Long outputPageId;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
