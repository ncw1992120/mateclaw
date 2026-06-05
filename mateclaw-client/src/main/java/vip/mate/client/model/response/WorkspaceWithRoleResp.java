package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 带角色信息的工作区
 */
@Data
public class WorkspaceWithRoleResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String basePath;
    private Long ownerId;
    private String settingsJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String memberRole;
    private Integer roleLevel;
    private Boolean isGlobalAdmin;
    private String effectiveRole;
}
