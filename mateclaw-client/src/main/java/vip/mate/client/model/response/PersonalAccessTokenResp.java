package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人访问令牌
 */
@Data
public class PersonalAccessTokenResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 人类可读标签 */
    private String name;

    /** 逗号分隔的权限范围 */
    private String scopes;

    /** 最后使用时间 */
    private LocalDateTime lastUsedAt;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 软删除标记 */
    private Integer deleted;
}
