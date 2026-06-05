package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计事件
 */
@Data
public class AuditEventResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workspaceId;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String detailJson;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createTime;
}
