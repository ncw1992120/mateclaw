package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 技能实体
 */
@Data
public class Skill implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String nameZh;
    private String nameEn;
    private String description;
    private String skillType;
    private String icon;
    private String version;
    private String author;
    private String configJson;
    private String sourceCode;
    private String skillContent;
    private String manifestJson;
    private Boolean enabled;
    private Boolean builtin;
    private String tags;
    private Long workspaceId;
    private String sourceConversationId;
    private String securityScanStatus;
    private String securityScanResult;
    private LocalDateTime securityScanTime;
    private String lifecycleState;
    private Boolean pinned;
    private LocalDateTime lastActivityAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
