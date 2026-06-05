package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人访问令牌创建请求
 */
@Data
public class PatCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 标签 */
    private String name;

    /** 权限范围 */
    private String scopes;

    /** 过期时间 */
    private LocalDateTime expiresAt;
}
