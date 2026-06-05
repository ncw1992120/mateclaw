package vip.mate.client.model.request;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Provider 配置更新请求
 */
@Data
public class ProviderConfigReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** API Key */
    private String apiKey;

    /** 基础 URL */
    private String baseUrl;

    /** 协议 */
    private String protocol;

    /** 默认聊天模型 */
    private String chatModel;

    /** 生成参数 */
    private Map<String, Object> generateKwargs;

    /** 是否需要 API Key */
    private Boolean requireApiKey;

    /** 故障转移优先级 */
    private Integer fallbackPriority;
}
