package vip.mate.client.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 模型 Provider 信息
 */
@Data
public class ProviderInfoResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Provider ID */
    private String id;

    /** Provider 名称 */
    private String name;

    /** 协议 */
    private String protocol;

    /** API Key 前缀 */
    private String apiKeyPrefix;

    /** 默认聊天模型 */
    private String chatModel;

    /** 内置模型列表 */
    private List<ModelInfoResp> models;

    /** 额外添加的模型列表 */
    private List<ModelInfoResp> extraModels;

    /** 是否自定义 Provider */
    private Boolean isCustom;

    /** 是否本地模型 */
    private Boolean isLocal;

    /** 是否支持模型发现 */
    private Boolean supportModelDiscovery;

    /** 是否支持连接测试 */
    private Boolean supportConnectionCheck;

    /** 是否冻结 URL */
    private Boolean freezeUrl;

    /** 是否需要 API Key */
    private Boolean requireApiKey;

    /** 是否已配置 */
    private Boolean configured;

    /** 是否可用 */
    private Boolean available;

    /** API Key */
    private String apiKey;

    /** 基础 URL */
    private String baseUrl;

    /** 生成参数 */
    private Map<String, Object> generateKwargs;

    /** 认证类型 */
    private String authType;

    /** OAuth 是否已连接 */
    private Boolean oauthConnected;

    /** OAuth 过期时间(epoch ms) */
    private Long oauthExpiresAt;

    /** 故障转移优先级 */
    private Integer fallbackPriority;

    /** 运行时存活状态 */
    private String liveness;

    /** 不可用原因 */
    private String unavailableReason;

    /** 最近探测时间(epoch ms) */
    private Long lastProbedAtMs;

    /** 剩余冷却时间(ms) */
    private Long cooldownRemainingMs;

    /** 是否显式启用 */
    private Boolean enabled;

    /** 凭证状态 */
    private String authStatus;

    /** Base URL 是否完整 */
    private Boolean baseUrlComplete;

    /** 缺失字段名 */
    private String missingFields;

    /** 建议下一步操作键值 */
    private String suggestedAction;

    /** i18n 提示键 */
    private String suggestedActionHintKey;

    /** i18n 提示参数 */
    private Map<String, Object> suggestedActionHintArgs;
}
