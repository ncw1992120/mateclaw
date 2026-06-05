package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型提供商
 */
@Data
public class ModelProviderResp implements Serializable {
    private static final long serialVersionUID = 1L;

    private String providerId;
    private String name;
    private String apiKeyPrefix;
    private String chatModel;
    private String apiKey;
    private String baseUrl;
    private String generateKwargs;
    private Boolean isCustom;
    private Boolean isLocal;
    private Boolean supportModelDiscovery;
    private Boolean supportConnectionCheck;
    private Boolean freezeUrl;
    private Boolean requireApiKey;
    private String authType;
    private String oauthAccessToken;
    private String oauthRefreshToken;
    private Long oauthExpiresAt;
    private String oauthAccountId;
    private Integer fallbackPriority;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
