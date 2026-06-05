package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流实体
 */
@Data
public class Workflow implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private String name;
    private String description;
    private Boolean enabled;
    private String draftJson;
    private String draftSchemaVersion;
    private Long draftUpdatedBy;
    private LocalDateTime draftUpdatedAt;
    private Long latestRevisionId;
    private String publishedGraphJson;
    private Integer latestRevisionNumber;
    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
