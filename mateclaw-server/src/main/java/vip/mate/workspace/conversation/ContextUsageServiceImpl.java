package vip.mate.workspace.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import vip.mate.agent.AgentToolSet;
import vip.mate.agent.binding.service.AgentBindingService;
import vip.mate.agent.context.TokenEstimator;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.config.ConversationWindowProperties;
import vip.mate.tool.ToolRegistry;
import vip.mate.workspace.conversation.model.ConversationEntity;
import vip.mate.workspace.conversation.model.MessageEntity;
import vip.mate.workspace.conversation.vo.ContextCompressionStatusVO;
import vip.mate.workspace.conversation.vo.ContextUsageCategoryVO;
import vip.mate.workspace.conversation.vo.ContextUsageVO;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上下文使用查询服务实现
 * <p>
 * 通过内存缓存保存最近一次对话时的精确上下文占用，
 * 无缓存时基于历史消息、Agent 系统提示词和工具定义做兜底估算。
 */
@Slf4j
@Service
public class ContextUsageServiceImpl implements ContextUsageService {

    private final ConversationService conversationService;
    private final AgentMapper agentMapper;
    private final ConversationWindowProperties properties;

    /**
     * 工具注册中心，用于兜底估算时获取工具定义 token。
     * <p>
     * 使用 @Lazy 避免与 AgentBindingService / ToolRegistry 初始化链产生循环依赖。
     */
    @Lazy
    @Autowired
    private ToolRegistry toolRegistry;

    /**
     * Agent 能力绑定服务，用于兜底估算时按 Agent 绑定过滤工具集。
     * <p>
     * 使用 @Lazy 避免循环依赖。
     */
    @Lazy
    @Autowired
    private AgentBindingService agentBindingService;

    /**
     * 会话上下文使用缓存：conversationId -> ContextUsageVO
     */
    private final ConcurrentHashMap<String, ContextUsageVO> usageCache = new ConcurrentHashMap<>();

    @Autowired
    public ContextUsageServiceImpl(ConversationService conversationService,
                                   AgentMapper agentMapper,
                                   ConversationWindowProperties properties) {
        this.conversationService = conversationService;
        this.agentMapper = agentMapper;
        this.properties = properties;
    }

    @Override
    public ContextUsageVO getContextUsage(String conversationId) {
        ContextUsageVO cached = usageCache.get(conversationId);
        if (cached != null) {
            return cached;
        }
        return estimateFallback(conversationId);
    }

    @Override
    public void recordContextUsage(String conversationId, ContextUsageVO usage) {
        if (conversationId == null || conversationId.isBlank() || usage == null) {
            return;
        }
        usage.setConversationId(conversationId);
        usage.setTimestamp(System.currentTimeMillis());
        usageCache.put(conversationId, usage);
    }

    /**
     * 无缓存时的兜底估算
     * <p>
     * 基于 Agent 系统提示词 + 工具定义 + 历史消息 promptTokens 累加估算。
     * 工具定义 token 根据该 Agent 绑定的工具集实时计算。
     * 兜底值不含当前正在生成的消息，因此数值可能略低于实际发送给 LLM 的 token 数。
     */
    private ContextUsageVO estimateFallback(String conversationId) {
        int contextWindow = properties != null ? properties.getDefaultMaxInputTokens() : 128000;
        ConversationEntity conversation = conversationService.findByConversationId(conversationId);
        if (conversation == null) {
            return ContextUsageVO.empty(conversationId, contextWindow);
        }

        // 读取会话关联的 Agent 系统提示词
        String systemPrompt = null;
        Long agentId = conversation.getAgentId();
        if (agentId != null) {
            AgentEntity agent = agentMapper.selectById(agentId);
            if (agent != null) {
                systemPrompt = agent.getSystemPrompt();
                if (agent.getModelName() != null && !agent.getModelName().isBlank()) {
                    // 若 Agent 覆盖模型，这里仍使用全局默认窗口；
                    // 精确窗口需在运行时由 ModelConfigService 解析，为简化兜底逻辑暂用默认值。
                    contextWindow = properties != null ? properties.getDefaultMaxInputTokens() : 128000;
                }
            }
        }

        // 估算工具定义 token
        int toolsTokens = estimateToolsTokensForAgent(agentId);

        // 累加历史消息的 promptTokens
        List<MessageEntity> messages = conversationService.listMessages(conversationId);
        int conversationTokens = messages.stream()
                .mapToInt(m -> m.getPromptTokens() != null ? m.getPromptTokens() : 0)
                .sum();

        int systemTokens = TokenEstimator.estimateTokens(systemPrompt != null ? systemPrompt : "");
        int usedTokens = systemTokens + toolsTokens + conversationTokens;
        double usedPercent = contextWindow > 0 ? Math.min(1.0, (double) usedTokens / contextWindow) : 0.0;

        ContextUsageVO vo = new ContextUsageVO();
        vo.setConversationId(conversationId);
        vo.setContextWindow(contextWindow);
        vo.setUsedTokens(usedTokens);
        vo.setUsedPercent(usedPercent);
        vo.setCategories(List.of(
                ContextUsageCategoryVO.of("system_prompt", "System prompt", systemTokens, "#9ca3af"),
                ContextUsageCategoryVO.of("tool_definitions", "Tool definitions", toolsTokens, "#8b5cf6"),
                ContextUsageCategoryVO.of("conversation", "Conversation", conversationTokens, "#f87171")));
        vo.setCompression(ContextCompressionStatusVO.none());
        vo.setTimestamp(System.currentTimeMillis());
        return vo;
    }

    /**
     * 估算指定 Agent 绑定的工具定义 token 数。
     * <p>
     * 根据 Agent 的工具绑定白名单过滤全局工具集，然后估算 token。
     * 若无法获取工具集（依赖未注入或异常），返回 0 而不阻塞主流程。
     *
     * @param agentId Agent ID，为 null 时返回 0
     * @return 工具定义的估算 token 数
     */
    private int estimateToolsTokensForAgent(Long agentId) {
        if (agentId == null || toolRegistry == null || agentBindingService == null) {
            return 0;
        }
        try {
            AgentToolSet globalSet = toolRegistry.getEnabledToolSet();
            Set<String> allowedTools = agentBindingService.getEffectiveToolNames(agentId);
            AgentToolSet filtered = globalSet.withAllowedToolsOnly(allowedTools);
            List<ToolCallback> callbacks = filtered.callbacks();
            return TokenEstimator.estimateToolsTokens(callbacks);
        } catch (Exception e) {
            log.debug("[ContextUsage] estimate tools tokens failed for agent {}: {}", agentId, e.getMessage());
            return 0;
        }
    }
}
