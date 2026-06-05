package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 工作区访问权限
 */
@Data
public class WorkspaceAccessResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long workspaceId;
    private String memberRole;
    private Boolean isGlobalAdmin;
    private String effectiveRole;
    private Set<String> capabilities;
}
