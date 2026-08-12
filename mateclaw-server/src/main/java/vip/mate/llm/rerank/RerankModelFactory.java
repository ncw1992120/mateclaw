package vip.mate.llm.rerank;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vip.mate.exception.MateClawException;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.model.ModelProviderEntity;
import vip.mate.llm.model.RerankProtocol;
import vip.mate.llm.service.ModelProviderService;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rerank 模型工厂
 * <p>
 * 根据 {@link ModelConfigEntity}（model_type='rerank'）构造对应的 {@link RerankModel}：
 * <ul>
 *   <li>DashScope：复用 provider UI 配置的 apiKey/baseUrl，构造 {@link DashScopeRerankModel}</li>
 *   <li>OpenAI 兼容（Cohere / Jina 等）：构造 {@link OpenAiRerankModel}</li>
 * </ul>
 * <p>
 * 设计要点（对齐 {@code EmbeddingModelFactory}）：
 * <ol>
 *   <li><b>Provider api_key 共用</b>：与 chat/embedding 模型共用 {@code mate_model_provider.api_key}</li>
 *   <li><b>缓存</b>：按 ModelConfigEntity.id 缓存 RerankModel 实例</li>
 *   <li><b>回落</b>：provider apiKey 未配时，DashScope 走 yml 的 {@code spring.ai.dashscope.api-key}</li>
 * </ol>
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankModelFactory {

    private final ModelProviderService providerService;
    private final DashScopeConnectionProperties dashScopeConnectionProperties;
    private final ObjectMapper objectMapper;

    /** 按 modelConfig.id 缓存 RerankModel，config 变更时调用 {@link #evict} 清除 */
    private final ConcurrentHashMap<Long, RerankModel> cache = new ConcurrentHashMap<>();

    /**
     * 构造或复用指定配置对应的 RerankModel
     *
     * @throws MateClawException 当 provider 未配置或协议不支持时
     */
    public RerankModel build(ModelConfigEntity modelConfig) {
        if (modelConfig == null) {
            throw new MateClawException("err.rerank.config_null", "Rerank model config is null");
        }
        if (modelConfig.getId() != null) {
            RerankModel cached = cache.get(modelConfig.getId());
            if (cached != null) {
                return cached;
            }
        }

        RerankModel fresh = doBuild(modelConfig);
        if (modelConfig.getId() != null) {
            cache.put(modelConfig.getId(), fresh);
        }
        return fresh;
    }

    /**
     * 清除指定配置的缓存实例（provider api_key 变更 / 模型切换时调用）
     */
    public void evict(Long modelConfigId) {
        if (modelConfigId != null) {
            cache.remove(modelConfigId);
        }
    }

    /**
     * 从 provider 的 chatModel 列解析 rerank 协议（与 Embedding 协议同一信号源）
     */
    static RerankProtocol resolveRerankProtocol(String chatModel) {
        if (chatModel == null || chatModel.isBlank()) {
            return RerankProtocol.OPENAI_RERANK;
        }
        return "DashScopeChatModel".equalsIgnoreCase(chatModel.trim())
                ? RerankProtocol.DASHSCOPE_RERANK
                : RerankProtocol.OPENAI_RERANK;
    }

    // ==================== 内部实现 ====================

    private RerankModel doBuild(ModelConfigEntity modelConfig) {
        ModelProviderEntity provider = providerService.getProviderConfig(modelConfig.getProvider());
        if (provider == null) {
            throw new MateClawException("err.rerank.provider_missing",
                    "Rerank provider '" + modelConfig.getProvider() + "' not found in mate_model_provider");
        }

        RerankProtocol protocol = resolveRerankProtocol(provider.getChatModel());
        log.info("[RerankFactory] Building rerank model: provider={}, chatModel={}, model={}, protocol={}",
                provider.getProviderId(), provider.getChatModel(), modelConfig.getModelName(), protocol);

        return switch (protocol) {
            case DASHSCOPE_RERANK -> buildDashScope(provider, modelConfig);
            case OPENAI_RERANK -> buildOpenAi(provider, modelConfig);
        };
    }

    private RerankModel buildDashScope(ModelProviderEntity provider, ModelConfigEntity modelConfig) {
        // API Key 回落链：provider UI → yml
        String apiKey = provider.getApiKey();
        if (!StringUtils.hasText(apiKey) || !providerService.hasUsableApiKey(apiKey)) {
            apiKey = dashScopeConnectionProperties.getApiKey();
        }
        if (!providerService.hasUsableApiKey(apiKey)) {
            throw new MateClawException("err.rerank.dashscope_key_missing",
                    "DashScope API Key 未配置。请在模型设置中填写 dashscope provider 的 API Key。");
        }

        String baseUrl = provider.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = dashScopeConnectionProperties.getBaseUrl();
        }
        return new DashScopeRerankModel(apiKey.trim(), baseUrl, modelConfig.getModelName(), objectMapper);
    }

    private RerankModel buildOpenAi(ModelProviderEntity provider, ModelConfigEntity modelConfig) {
        if (!providerService.isProviderConfigured(provider.getProviderId())) {
            throw new MateClawException("err.rerank.provider_not_configured",
                    "Provider '" + provider.getProviderId() + "' 未完成配置（缺少 API Key 或 Base URL）");
        }
        String apiKey = provider.getApiKey();
        boolean keyRequired = !Boolean.FALSE.equals(provider.getRequireApiKey());
        if (keyRequired && !providerService.hasUsableApiKey(apiKey)) {
            throw new MateClawException("err.rerank.openai_key_invalid",
                    "Provider API Key 未配置或无效: " + provider.getProviderId());
        }
        String baseUrl = normalizeOpenAiBaseUrl(provider.getBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new MateClawException("err.rerank.openai_baseurl_missing",
                    "Provider Base URL 未配置: " + provider.getProviderId());
        }
        return new OpenAiRerankModel(apiKey, baseUrl, modelConfig.getModelName(), objectMapper);
    }

    /**
     * 归一化 OpenAI 兼容 base URL：仅去除首尾空白与末尾斜杠。
     * <p>
     * 保留 baseUrl 中的 /v1 版本前缀（如 Xinference /v1/rerank、Jina /v1/rerank），
     * 由用户按部署服务的实际端点决定是否携带；端点统一拼接为 {@code {baseUrl}/rerank}
     * （TeAI / Cohere 等无 /v1 前缀的服务直接填根地址即可）。
     */
    private String normalizeOpenAiBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
