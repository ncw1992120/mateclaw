package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流版本快照
 */
@Data
public class WorkflowRevisionResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属工作流 ID */
    private Long workflowId;

    /** 版本号 */
    private Integer revision;

    /** 发布时的 graph JSON 快照 */
    private String graphJson;

    /** schema 版本 */
    private String schemaVersion;

    /** 发布备注 */
    private String publishedNote;

    /** 发布人 */
    private Long publishedBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
