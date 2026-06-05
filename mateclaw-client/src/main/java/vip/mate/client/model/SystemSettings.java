package vip.mate.client.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 系统设置
 */
@Data
public class SystemSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    private String language;
    private Boolean streamEnabled;
    private Boolean debugMode;
    private Boolean stateGraphEnabled;
    private Boolean searchEnabled;
    private String searchProvider;
    private Boolean searchFallbackEnabled;
    private String serperApiKey;
    private String serperBaseUrl;
    private String tavilyApiKey;
    private String tavilyBaseUrl;
    private Boolean duckduckgoEnabled;
    private String searxngBaseUrl;
    private String serperApiKeyMasked;
    private String tavilyApiKeyMasked;
    private Boolean videoEnabled;
    private String videoProvider;
    private Boolean videoFallbackEnabled;
    private String zhipuApiKey;
    private String zhipuBaseUrl;
    private String zhipuApiKeyMasked;
    private String falApiKey;
    private String falApiKeyMasked;
    private String klingAccessKey;
    private String klingSecretKey;
    private String klingAccessKeyMasked;
    private String klingSecretKeyMasked;
    private String runwayApiKey;
    private String runwayApiKeyMasked;
    private String minimaxApiKey;
    private String minimaxApiKeyMasked;
    private String minimaxRegion;
    private Boolean imageEnabled;
    private String imageProvider;
    private Boolean imageFallbackEnabled;
    private Boolean ttsEnabled;
    private String ttsProvider;
    private Boolean ttsFallbackEnabled;
    private String ttsAutoMode;
    private String ttsDefaultVoice;
    private Double ttsSpeed;
    private Boolean sttEnabled;
    private String sttProvider;
    private Boolean sttFallbackEnabled;
    private String sttOpenAiCompatProviderId;
    private String sttOpenAiCompatModel;
    private Boolean musicEnabled;
    private String musicProvider;
    private Boolean musicFallbackEnabled;
    private Boolean model3dEnabled;
    private String model3dProvider;
    private Boolean model3dFallbackEnabled;
    private Long defaultVisionModelId;
    private Long defaultVideoModelId;
}
