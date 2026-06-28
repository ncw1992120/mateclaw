package vip.mate.dataagent.dto;

import lombok.Data;

/**
 * 资源授权请求 DTO
 */
@Data
public class ResourceGrantRequest {

    /** 资源类型 */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 授权类型：role / user / group */
    private String grantType;

    /** 被授权者标识 */
    private String granteeId;

    /** 权限：use / manage / publish */
    private String permission;

    /** 过期时间（ISO 格式字符串，NULL 表示永久） */
    private String expireTime;
}
