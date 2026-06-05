package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人访问令牌创建结果
 */
@Data
public class PatCreateResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 新创建的 token ID */
    private Long id;

    /** 明文令牌(仅此一次返回) */
    private String plaintext;

    /** 标签 */
    private String name;

    /** 权限范围 */
    private String scopes;

    /** 过期时间 */
    private Object expiresAt;
}
