package vip.mate.dataagent.agentscope.service.impl;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import vip.mate.dataagent.agentscope.AgentScopeConstants;
import vip.mate.dataagent.agentscope.dto.AgentCallRequest;
import vip.mate.dataagent.agentscope.dto.AgentCallResponse;
import vip.mate.dataagent.agentscope.service.AgentScopeService;
import vip.mate.dataagent.service.DataAgentModelService;
import vip.mate.llm.model.ActiveModelsInfo;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.model.ModelFamily;
import vip.mate.llm.model.ModelProtocol;
import vip.mate.llm.model.ModelProviderEntity;
import vip.mate.llm.service.ModelProviderService;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AgentScope Agent 调用服务实现
 * <p>
 * 封装 AgentScope Java SDK 的 ReActAgent 流式调用能力。
 * 模型配置通过后端已有的模型管理接口获取（MateClawRuntime + ModelProviderService），
 * 根据模型 Provider 的协议类型自动构建对应的 AgentScope Model 实例。
 */
@Slf4j
@Service
public class AgentScopeServiceImpl implements AgentScopeService {

    private final DataAgentModelService modelService;
    private final ModelProviderService modelProviderService;

    public AgentScopeServiceImpl(MateClawRuntime runtime,
                                  DataAgentModelService modelService,
                                  ModelProviderService modelProviderService) {
        this.modelService = modelService;
        this.modelProviderService = modelProviderService;
    }

    @Override
    public Flux<Event> streamCall(AgentCallRequest request) {
        // 解析模型配置
        ResolvedModel resolved = resolveModel(request.getModelProvider(), request.getModelName());
        // 构建 AgentScope Model
        Model agentModel = buildAgentScopeModel(resolved);
        // 构建 ReActAgent
        String agentName = resolveAgentName(request.getAgentName());
        String systemPrompt = resolveSystemPrompt(request.getSystemPrompt());
        ReActAgent agent = ReActAgent.builder()
                .name(agentName)
                .sysPrompt(systemPrompt)
                .model(agentModel)
                .build();
        // 构建消息列表：历史对话消息 + 当前用户消息
        List<Msg> msgList = new ArrayList<>();
        if (request.getHistoryMessages() != null) {
            for (AgentCallRequest.HistoryMessage historyMsg : request.getHistoryMessages()) {
                if (historyMsg.getContent() == null || historyMsg.getContent().isBlank()) {
                    continue;
                }
                MsgRole role = "assistant".equalsIgnoreCase(historyMsg.getRole())
                        ? MsgRole.ASSISTANT
                        : MsgRole.USER;
                msgList.add(Msg.builder()
                        .role(role)
                        .textContent(historyMsg.getContent())
                        .build());
            }
        }
        // 当前用户消息
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(request.getMessage())
                .build();
        msgList.add(userMsg);
        // 流式调用，订阅所有事件类型
        return agent.stream(msgList, StreamOptions.builder().eventTypes(EventType.ALL).build());
    }

    @Override
    public AgentCallResponse call(AgentCallRequest request) {
        AgentCallResponse response = new AgentCallResponse();
        try {
            // 解析模型配置
            ResolvedModel resolved = resolveModel(request.getModelProvider(), request.getModelName());
            // 通过流式调用阻塞收集最终结果
            AtomicReference<String> lastContent = new AtomicReference<>("");
            streamCall(request)
                    .filter(event -> event.getType() == EventType.AGENT_RESULT && event.getMessage() != null)
                    .doOnNext(event -> lastContent.set(event.getMessage().getTextContent()))
                    .blockLast();
            // 填充响应
            String sessionId = resolveSessionId(request.getSessionId());
            response.setContent(lastContent.get());
            response.setModelName(resolved.modelName);
            response.setModelProvider(resolved.providerId);
            response.setSessionId(sessionId);
            response.setSuccess(true);
        } catch (Exception e) {
            log.error("[AgentScope] Agent 调用失败: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
        }
        return response;
    }

    /**
     * 解析模型配置：优先使用请求指定的模型，否则使用后端激活模型
     */
    private ResolvedModel resolveModel(String requestProvider, String requestModelName) {
        // 优先使用请求中指定的模型
        if (StringUtils.hasText(requestProvider) && StringUtils.hasText(requestModelName)) {
            ModelProviderEntity provider = modelProviderService.getProviderConfig(requestProvider);
            return new ResolvedModel(requestProvider, requestModelName, provider);
        }
        // 其次使用激活模型
        ActiveModelsInfo activeInfo = modelService.getActiveModelSafe();
        if (activeInfo != null && activeInfo.getActiveLlm() != null) {
            String providerId = activeInfo.getActiveLlm().getProviderId();
            String modelName = activeInfo.getActiveLlm().getModel();
            if (StringUtils.hasText(providerId) && StringUtils.hasText(modelName)) {
                ModelProviderEntity provider = modelProviderService.getProviderConfig(providerId);
                return new ResolvedModel(providerId, modelName, provider);
            }
        }
        // 最后使用默认模型
        ModelConfigEntity defaultModel = modelService.getDefaultModelSafe();
        if (defaultModel != null) {
            String providerId = defaultModel.getProvider();
            String modelName = defaultModel.getModelName();
            ModelProviderEntity provider = modelProviderService.getProviderConfig(providerId);
            return new ResolvedModel(providerId, modelName, provider);
        }
        throw new IllegalStateException("无可用的模型配置，请在模型管理中配置并激活模型");
    }

    /**
     * 根据模型 Provider 协议构建 AgentScope Model 实例
     * <p>
     * 支持的协议映射：
     * <ul>
     *   <li>DASHSCOPE_NATIVE → DashScopeChatModel</li>
     *   <li>OPENAI_COMPATIBLE / OPENAI_CHATGPT → OpenAIChatModel</li>
     *   <li>ANTHROPIC_MESSAGES → AnthropicChatModel</li>
     *   <li>GEMINI_NATIVE → OpenAIChatModel（Gemini 兼容 OpenAI 格式）</li>
     * </ul>
     */
    private Model buildAgentScopeModel(ResolvedModel resolved) {
        ModelProtocol protocol = ModelProtocol.fromChatModel(resolved.provider.getChatModel());
        switch (protocol) {
            case DASHSCOPE_NATIVE:
                return buildDashScopeModel(resolved);
            case OPENAI_COMPATIBLE:
            case OPENAI_CHATGPT:
                return buildOpenAICompatibleModel(resolved);
            case ANTHROPIC_MESSAGES:
            case ANTHROPIC_CLAUDE_CODE:
                return buildAnthropicModel(resolved);
            case GEMINI_NATIVE:
                // Gemini 使用 OpenAI 兼容接口
                return buildOpenAICompatibleModel(resolved);
            default:
                log.warn("[AgentScope] 协议 {} 未原生支持，尝试使用 OpenAI 兼容模式", protocol.getId());
                return buildOpenAICompatibleModel(resolved);
        }
    }

    /**
     * 构建 DashScope 模型
     */
    private Model buildDashScopeModel(ResolvedModel resolved) {
        String apiKey = resolved.provider.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DashScope Provider 未配置 API Key");
        }
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(resolved.modelName)
                .enableThinking(true)
                .stream(true)
                .build();
    }

    /**
     * 构建 OpenAI 兼容模型（适用于所有 OpenAI API 格式的 Provider）
     * <p>
     * 思考模式适配参考 mateclaw-server 的 {@link ModelFamily} 体系：
     * {@link OpenAIChatFormatter#applyOptions} 不会处理 {@code thinkingBudget}，
     * 故思考开关必须通过 {@code GenerateOptions.additionalBodyParams}（经
     * {@code @JsonAnySetter} 平铺到请求体顶层）按模型族 + Provider 注入对应字段：
     * <ul>
     *   <li>{@link ModelFamily#DEEPSEEK_V4_REASONING}：注入 {@code thinking={type:enabled}}
     *       + {@code reasoning_effort=medium}（对齐 server 的 DeepSeekV4ThinkingDecorator）</li>
     *   <li>{@link ModelFamily#OPENAI_REASONING}：注入 {@code reasoning_effort=medium}</li>
     *   <li>DashScope 兼容模式下的非 thinking 后缀 qwen：注入 {@code enable_thinking=true}
     *       （Qwen3 在兼容模式下默认可能不输出思考，需显式开启）</li>
     *   <li>其余 thinking 家族（deepseek-reasoner / kimi-k2 / mimo / qwen-thinking 等）：
     *       天然输出 {@code reasoning_content}，无需注入开关</li>
     *   <li>{@link ModelFamily#STANDARD}：非思考模型，不注入</li>
     * </ul>
     */
    private Model buildOpenAICompatibleModel(ResolvedModel resolved) {
        String apiKey = resolved.provider.getApiKey();
        String baseUrl = resolved.provider.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("OpenAI 兼容 Provider 未配置 Base URL");
        }
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder();
        applyThinkingOptions(optionsBuilder, resolved);
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .modelName(resolved.modelName)
                .baseUrl(baseUrl)
                .generateOptions(optionsBuilder.build())
                .stream(true);
        if (StringUtils.hasText(apiKey)) {
            builder.apiKey(apiKey);
        }
        return builder.build();
    }

    /**
     * 按模型族 + Provider 向 GenerateOptions 注入开启思考所需的请求体参数。
     * <p>
     * 仅对需要显式开关的家族注入；天然 thinking 的家族与标准模型保持空注入，
     * 避免向不识别该字段的 Provider 发送多余参数触发 400。
     */
    private void applyThinkingOptions(GenerateOptions.Builder optionsBuilder, ResolvedModel resolved) {
        String modelName = resolved.modelName;
        ModelFamily family = ModelFamily.detect(modelName);

        // 1. 需要显式开关的家族
        switch (family) {
            case DEEPSEEK_V4_REASONING -> {
                // DeepSeek V4 扩展字段：thinking 开关 + reasoning_effort（仅 thinking=enabled 时有意义）
                optionsBuilder.additionalBodyParam("thinking", Map.of("type", "enabled"));
                optionsBuilder.reasoningEffort("medium");
                log.info("[AgentScope] 模型 {} 命中 {}，注入 thinking=enabled + reasoning_effort=medium",
                        modelName, family);
                return;
            }
            case OPENAI_REASONING -> {
                // gpt-5*/o1*/o3*/o4* 通过 reasoning_effort 控制推理强度
                optionsBuilder.reasoningEffort("medium");
                log.info("[AgentScope] 模型 {} 命中 {}，注入 reasoning_effort=medium", modelName, family);
                return;
            }
            case DEEPSEEK_REASONER, KIMI_THINKING, MIMO_THINKING, GENERIC_THINKING -> {
                // 天然输出 reasoning_content 的 thinking 家族，无需注入开关
                // 注意：qwen3-*-thinking-* 归为 GENERIC_THINKING，对 enable_thinking 参数会 400，不可注入
                log.debug("[AgentScope] 模型 {} 命中 {}，天然 thinking，无需显式开启", modelName, family);
                return;
            }
            default -> { /* 落到下面的 Provider 级适配 */ }
        }

        // 2. DashScope 兼容模式：默认注入 enable_thinking=true
        //    DashScope 兼容模式下的 chat 模型（qwen-plus/qwen3-max/qwen3.6-plus 等）均支持 enable_thinking，
        //    且该参数对不支持思考的模型会被安全忽略（不报 400）。
        //    天然 thinking 家族已在上面 switch 中 return，不会走到这里。
        if (isDashScopeCompatible(resolved) || modelName.toLowerCase().contains("qwen")) {
            optionsBuilder.additionalBodyParam("enable_thinking", true);
            log.info("[AgentScope] DashScope 兼容模型 {} 注入 enable_thinking=true", modelName);
            return;
        }

        log.debug("[AgentScope] 模型 {} 命中 {}，无需显式开启思考（标准模型）",
                modelName, family);
    }

    /**
     * 判定是否 DashScope OpenAI 兼容模式 Provider。
     * <p>
     * 兼容 providerId 前缀（dashscope-compat / aliyun-codingplan*）或 baseUrl 含
     * {@code compatible-mode}。
     */
    private boolean isDashScopeCompatible(ResolvedModel resolved) {
        String providerId = resolved.providerId != null ? resolved.providerId.toLowerCase() : "";
        if (providerId.startsWith("dashscope-compat")
                || providerId.startsWith("aliyun-codingplan")) {
            return true;
        }
        String baseUrl = resolved.provider.getBaseUrl();
        return baseUrl != null && baseUrl.contains("compatible-mode");
    }

    /**
     * 构建 Anthropic 模型
     */
    private Model buildAnthropicModel(ResolvedModel resolved) {
        String apiKey = resolved.provider.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Anthropic Provider 未配置 API Key");
        }
        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(resolved.modelName)
                .stream(true);
        String baseUrl = resolved.provider.getBaseUrl();
        if (StringUtils.hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private String resolveAgentName(String agentName) {
        return StringUtils.hasText(agentName) ? agentName : AgentScopeConstants.DEFAULT_AGENT_NAME;
    }

    private String resolveSystemPrompt(String systemPrompt) {
        return StringUtils.hasText(systemPrompt) ? systemPrompt : AgentScopeConstants.DEFAULT_SYSTEM_PROMPT;
    }

    private String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : AgentScopeConstants.DEFAULT_SESSION_ID;
    }

    private String resolveUserId(String userId) {
        return StringUtils.hasText(userId) ? userId : AgentScopeConstants.DEFAULT_USER_ID;
    }

    /**
     * 解析后的模型信息
     */
    private record ResolvedModel(String providerId, String modelName, ModelProviderEntity provider) {}
}
