package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 实体
 */
@Data
public class Agent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String agentType;
    private String systemPrompt;
    private String modelName;
    private Integer maxIterations;
    private Boolean enabled;
    private String icon;
    private String tags;
    private Long workspaceId;
    private Long creatorUserId;
    private String defaultThinkingLevel;
    private String workspaceBasePath;
    private Boolean skillsDisabled;
    private Boolean toolsDisabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
