package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth 状态结果
 */
@Data
public class OAuthStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已连接 */
    private boolean connected;

    /** 是否已过期 */
    private boolean expired;

    /** 过期时间戳(epoch ms) */
    private Long expiresAt;
}
