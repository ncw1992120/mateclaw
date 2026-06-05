package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth 授权结果
 */
@Data
public class OAuthAuthorizeResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 授权 URL */
    private String authorizeUrl;

    /** PKCE state */
    private String state;

    /** 授权模式 (LOCAL/DEVICE_CODE/MANUAL_PASTE) */
    private String mode;
}
