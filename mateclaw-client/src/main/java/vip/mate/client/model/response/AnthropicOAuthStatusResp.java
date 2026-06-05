package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Anthropic OAuth 状态
 */
@Data
public class AnthropicOAuthStatusResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否已连接 */
    private boolean connected;

    /** 是否已过期 */
    private boolean expired;

    /** 过期时间戳(epoch ms) */
    private long expiresAtMs;

    /** 凭证来源 */
    private String source;
}
