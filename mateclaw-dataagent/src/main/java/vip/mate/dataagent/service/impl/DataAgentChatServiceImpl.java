package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.dataagent.auth.context.UserContext;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.auth.service.AgentGuard;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.dto.DatasourceVO;
import vip.mate.dataagent.service.BusinessTermEsService;
import vip.mate.dataagent.service.DataAgentChatService;
import vip.mate.dataagent.service.DataAgentStreamTracker;
import vip.mate.dataagent.service.DatasourceManageService;
import vip.mate.dataagent.support.DataAgentChatScopeContext;
import vip.mate.dataagent.support.Utf8SseEmitter;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.model.MessageContentPart;
import vip.mate.workspace.conversation.model.MessageEntity;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * DataAgent 聊天服务实现
 * <p>
 * 参考 ChatController 的流式架构：
 * <ul>
 *   <li>StreamAccumulator.accept() 累积与广播分离——锁内只做数据累积，锁外执行 SSE 推送</li>
 *   <li>通过 DataAgentStreamTracker.broadcast() 广播到所有 emitter 订阅者</li>
 *   <li>使用 Utf8SseEmitter 显式声明 charset=UTF-8</li>
 *   <li>register() + attach() 生产者-消费者解耦，支持重连回放</li>
 *   <li>心跳保活防止代理/Nginx idle timeout 中断长连接</li>
 *   <li>markFirstTokenReceived 自动调整心跳频率</li>
 *   <li>handleStreamFinalize 提交到 sseExecutor 执行，避免阻塞 Reactor 线程</li>
 *   <li>有界线程池防止线程泄漏</li>
 * </ul>
 */
@Slf4j
@Service
public class DataAgentChatServiceImpl implements DataAgentChatService {

    private final MateClawRuntime runtime;
    private final ConversationService conversationService;
    private final DataAgentStreamTracker streamTracker;
    private final ObjectMapper objectMapper;
    private final DataAgentChatScopeContext scopeContext;
    private final DatasourceManageService datasourceManageService;
    private final BusinessTermEsService businessTermEsService;
    private final WorkspaceGuard workspaceGuard;
    private final AgentGuard agentGuard;
    private final ExecutorService sseExecutor;

    public DataAgentChatServiceImpl(MateClawRuntime runtime,
                                    ConversationService conversationService,
                                    DataAgentStreamTracker streamTracker,
                                    ObjectMapper objectMapper,
                                    DataAgentChatScopeContext scopeContext,
                                    DatasourceManageService datasourceManageService,
                                    BusinessTermEsService businessTermEsService,
                                    WorkspaceGuard workspaceGuard,
                                    AgentGuard agentGuard) {
        this.runtime = runtime;
        this.conversationService = conversationService;
        this.streamTracker = streamTracker;
        this.objectMapper = objectMapper;
        this.scopeContext = scopeContext;
        this.datasourceManageService = datasourceManageService;
        this.businessTermEsService = businessTermEsService;
        this.workspaceGuard = workspaceGuard;
        this.agentGuard = agentGuard;
        // 有界线程池：核心 2 线程，最大 CPU*2 线程，队列容量 256，CallerRunsPolicy 防止静默丢弃
        int maxThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        this.sseExecutor = new ThreadPoolExecutor(
                2, maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                r -> {
                    Thread t = new Thread(r, "dataagent-sse");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 流式对话（含模型覆盖和数据源白名单）。
     * <p>
     * 通过 {@code modelProvider} + {@code modelName} 将用户选择的模型 pin 到 conversation 级别，
     * AgentService 的 {@code getOrBuildAgentForConversation} 按 (agentId, modelKey) 缓存不同模型变体，
     * 避免每次对话都 updateAgent + refreshAgent。
     */
    @Override
    public SseEmitter streamChat(Long agentId, String message, String conversationId,
                                 String modelProvider, String modelName, List<String> datasourceIds,
                                 List<MessageContentPart> contentParts) {
        // 校验 Agent 归属当前工作区，防止跨工作区越权访问（在 HTTP 线程内执行，UserContextHolder 仍有效）
        agentGuard.requireAgentInCurrentWorkspace(agentId);
        // 将 String 类型的数据源 ID 转换为 Long 类型
        List<Long> longIds = convertToLongIds(datasourceIds);
        // 把"用户勾选数据源"信息写入会话级上下文，供 DatasourceQueryTool 在工具执行阶段读取
        scopeContext.putDatasourceIds(conversationId, longIds);

        // 注入数据源白名单提示词，并在前端展示原始 message（持久化时仍存原文）
        String llmMessage = decorateMessageWithScope(message, longIds);
        // 将附件信息注入到发送给 LLM 的 prompt 中
        if (contentParts != null && !contentParts.isEmpty()) {
            log.info("[DataAgent] contentParts received: size={}, types={}", contentParts.size(),
                    contentParts.stream().map(p -> p != null ? p.getType() : "null").collect(Collectors.joining(",")));
        }
        llmMessage = buildPromptText(llmMessage, contentParts);
        final String finalLlmMessage = llmMessage;
        SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        // 在 HTTP 线程捕获用户身份，避免 sseExecutor 线程内 ThreadLocal 上下文丢失
        final String username = workspaceGuard.currentUsername();
        final Long workspaceId = workspaceGuard.currentWorkspaceId();
        // 捕获完整 UserContext，用于在 sseExecutor 线程中恢复 UserContextHolder，
        // 使 Agent 工具（如 AloudataCallTool、DatasourceQueryTool）能通过 UserContextHolder.getUserId() 获取当前用户
        final UserContext capturedUserContext = UserContextHolder.get();

        // Register RunState first (creates buffer + starts heartbeat), then attach emitter
        streamTracker.register(conversationId);
        streamTracker.attach(conversationId, emitter);
        registerEmitterCallbacks(emitter, conversationId, emitterDone);

        sseExecutor.execute(() -> {
            // 恢复用户上下文到当前线程，使工具层 UserContextHolder.getUserId() 可用
            if (capturedUserContext != null) {
                UserContextHolder.set(capturedUserContext);
            }
            StreamAccumulator accumulator = new StreamAccumulator();
            AtomicBoolean finalized = new AtomicBoolean(false);
            try {
                conversationService.getOrCreateConversation(conversationId, agentId, username, workspaceId);
                // Pin 模型到 conversation 级别，与 ChatController 保持一致。
                // AgentService.getOrBuildAgentForConversation 从 conversation 表读取 pinned model，
                // 按 (agentId, modelKey) 缓存不同模型变体，无需 updateAgent + refreshAgent。
                if (modelProvider != null && !modelProvider.isBlank()
                        && modelName != null && !modelName.isBlank()) {
                    conversationService.updateConversationModel(conversationId, modelProvider, modelName);
                }
                conversationService.saveMessage(conversationId, "user", message, contentParts);
                conversationService.updateStreamStatus(conversationId, "running");

                // Broadcast initial events through tracker (buffered + reach all subscribers)
                broadcastEvent(conversationId, "session", Map.of("conversationId", conversationId, "agentId", agentId));
                broadcastEvent(conversationId, "message_start", Map.of("role", "assistant"));
                broadcastEvent(conversationId, "stream_started", Map.of(
                        "conversationId", conversationId,
                        "timestamp", System.currentTimeMillis()
                ));

                Flux<StreamDelta> stream = runtime.chatStructuredStream(agentId, finalLlmMessage, conversationId);

                Disposable disposable = stream
                        .doOnNext(delta -> {
                            if (emitterDone.get()) {
                                return;
                            }
                            try {
                                List<PendingBroadcast> pending = accumulator.accept(delta, conversationId);
                                // 在锁外执行广播（JSON 序列化 + SSE 推送），避免 I/O 阻塞锁
                                for (PendingBroadcast pb : pending) {
                                    try {
                                        broadcastEvent(conversationId, pb.eventName(), pb.data());
                                    } catch (Exception e) {
                                        log.warn("[DataAgent] Failed to broadcast event {}: {}", pb.eventName(), e.getMessage());
                                    }
                                }
                                // Mark first token received to relax heartbeat cadence
                                if (!accumulator.firstTokenMarked) {
                                    accumulator.firstTokenMarked = true;
                                    streamTracker.markFirstTokenReceived(conversationId);
                                }
                            } catch (Exception e) {
                                log.warn("[DataAgent] SSE broadcast error for {}: {}", conversationId, e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            if (!finalized.compareAndSet(false, true)) return;
                            boolean wasStopped = streamTracker.isStopRequested(conversationId);
                            String persistStatus = wasStopped ? "stopped" : "completed";
                            // 提交到 sseExecutor 执行，避免阻塞 Reactor 线程
                            sseExecutor.execute(() ->
                                handleStreamFinalize(emitter, emitterDone, accumulator, conversationId, persistStatus, agentId, message));
                        })
                        .doOnError(e -> {
                            if (!finalized.compareAndSet(false, true)) return;
                            boolean isUserStop = e instanceof java.util.concurrent.CancellationException
                                    || (e.getCause() instanceof java.util.concurrent.CancellationException);
                            String status = isUserStop ? "stopped" : "failed";
                            log.warn("[DataAgent] Stream {} for conversation {}: {}", status, conversationId, e.getMessage());
                            // 提交到 sseExecutor 执行，避免阻塞 Reactor 线程
                            sseExecutor.execute(() ->
                                handleStreamFinalize(emitter, emitterDone, accumulator, conversationId, status, agentId, message));
                        })
                        .doOnCancel(() -> {
                            if (!finalized.compareAndSet(false, true)) return;
                            log.info("[DataAgent] Stream cancelled for conversation {}", conversationId);
                            // 提交到 sseExecutor 执行，避免阻塞 Reactor 线程
                            sseExecutor.execute(() ->
                                handleStreamFinalize(emitter, emitterDone, accumulator, conversationId, "stopped", agentId, message));
                        })
                        .subscribe(
                                chunk -> {},
                                err -> log.debug("[DataAgent] Subscription terminated: {}", err.getMessage()),
                                () -> log.debug("[DataAgent] Subscription completed: conversationId={}", conversationId)
                        );

                streamTracker.setDisposable(conversationId, disposable);
            } catch (Exception e) {
                log.error("[DataAgent] SSE setup error for {}: {}", conversationId, e.getMessage());
                try {
                    broadcastEvent(conversationId, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "unknown error"));
                } catch (Exception ignored) {}
                streamTracker.complete(conversationId);
                scopeContext.clear(conversationId);
                conversationService.updateStreamStatus(conversationId, "idle");
                completeEmitterQuietly(emitter, emitterDone);
            }
        });

        return emitter;
    }

    @Override
    public SseEmitter streamChatFromRequest(Long agentId, String message, String conversationId,
                                             String modelProvider, String modelName, List<String> datasourceIds,
                                             boolean reconnect, Long lastEventId,
                                             List<MessageContentPart> contentParts) {
        String convId = conversationId != null ? conversationId : "default";
        if (reconnect) {
            return reconnect(convId, lastEventId != null ? lastEventId : 0L);
        }
        return streamChat(agentId, message, convId, modelProvider, modelName, datasourceIds, contentParts);
    }

    @Override
    public SseEmitter reconnect(String conversationId, long lastEventId) {
        SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        registerEmitterCallbacks(emitter, conversationId, emitterDone);

        boolean attached = streamTracker.attach(conversationId, emitter, lastEventId);
        if (!attached) {
            // Stream not found — either completed-and-cleaned or never existed
            try {
                broadcastEvent(conversationId, "done", Map.of(
                        "conversationId", conversationId,
                        "status", "stream_not_local",
                        "message", "Stream is not active on this server."
                ));
            } catch (Exception ignored) {}
            try { emitter.complete(); } catch (Exception ignored) {}
            log.info("[DataAgent] Reconnect: no RunState for {}", conversationId);
            return emitter;
        }

        log.info("[DataAgent] Reconnect: client attached for {}, lastEventId={}", conversationId, lastEventId);
        return emitter;
    }

    /**
     * 同步对话（含模型覆盖和数据源白名单）。
     * <p>
     * 与流式对话一致，通过 conversation-pinned-model 机制覆盖模型，
     * 避免每次对话都 updateAgent + refreshAgent。
     */
    @Override
    public String chat(Long agentId, String message, String conversationId,
                       String modelProvider, String modelName, List<String> datasourceIds,
                       List<MessageContentPart> contentParts) {
        // 校验 Agent 归属当前工作区，防止跨工作区越权访问
        agentGuard.requireAgentInCurrentWorkspace(agentId);
        // 将 String 类型的数据源 ID 转换为 Long 类型
        List<Long> longIds = convertToLongIds(datasourceIds);
        scopeContext.putDatasourceIds(conversationId, longIds);
        // 同步对话在 HTTP 线程内执行，可直接获取用户上下文
        final String username = workspaceGuard.currentUsername();
        final Long workspaceId = workspaceGuard.currentWorkspaceId();
        try {
            conversationService.getOrCreateConversation(conversationId, agentId, username, workspaceId);
            if (modelProvider != null && !modelProvider.isBlank()
                    && modelName != null && !modelName.isBlank()) {
                conversationService.updateConversationModel(conversationId, modelProvider, modelName);
            }
            conversationService.saveMessage(conversationId, "user", message, contentParts);
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to save user message for conversation {}: {}", conversationId, e.getMessage());
        }

        StreamAccumulator accumulator = new StreamAccumulator();
        String llmMessage = decorateMessageWithScope(message, longIds);
        // 将附件信息注入到发送给 LLM 的 prompt 中
        llmMessage = buildPromptText(llmMessage, contentParts);
        Flux<StreamDelta> stream = runtime.chatStructuredStream(agentId, llmMessage, conversationId);

        try {
            stream.doOnNext(delta -> accumulator.accept(delta, null)).blockLast();
        } finally {
            scopeContext.clear(conversationId);
        }

        String response = accumulator.getContent();
        List<MessageContentPart> parts = accumulator.toAssistantParts();
        try {
            conversationService.saveMessage(conversationId, "assistant", response, parts,
                    "completed", accumulator.getPromptTokens(), accumulator.getCompletionTokens(),
                    accumulator.getRuntimeModelName(), accumulator.getRuntimeProviderId(),
                    accumulator.toMetadataJson(objectMapper));
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to save assistant message for conversation {}: {}", conversationId, e.getMessage());
        }
        return response;
    }

    @Override
    public boolean requestStop(String conversationId) {
        boolean stopped = streamTracker.requestStop(conversationId);
        log.info("[DataAgent] Stop requested: conversationId={}, stopped={}", conversationId, stopped);
        return stopped;
    }

    /**
     * 基于当前对话上下文生成推荐问题。
     * <p>
     * 使用 MateClawRuntime 执行一个轻量级 LLM 调用，基于用户问题和AI回答摘要生成推荐追问问题。
     * 使用独立的会话ID前缀，避免污染用户真实会话历史。
     * 如果 LLM 调用失败，返回空列表。
     *
     * @param conversationId   会话ID（用于构建独立的推荐问题会话ID）
     * @param agentId          Agent ID
     * @param userMessage      当前用户问题
     * @param assistantSummary AI回答内容摘要
     * @return 推荐问题列表
     */
    @Override
    public List<String> generateRecommendedQuestions(String conversationId, Long agentId,
                                                     String userMessage, String assistantSummary) {
        if (agentId == null || userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        try {
            String prompt = DataAgentConstants.RECOMMENDED_QUESTION_PROMPT_TEMPLATE
                    .replace("{0}", userMessage)
                    .replace("{1}", assistantSummary != null ? assistantSummary : "");
            // 使用独立会话ID前缀，避免推荐问题的LLM调用污染用户真实会话历史
            String recConversationId = DataAgentConstants.RECOMMENDED_QUESTION_CONVERSATION_PREFIX + conversationId;
            String result = runtime.chat(agentId, prompt, recConversationId);
            if (result == null || result.isBlank()) {
                return List.of();
            }
            return parseRecommendedQuestions(result);
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to generate recommended questions: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 在用户消息前注入"数据源白名单"约束提示词。
     * <p>
     * 若用户在前端勾选了具体数据源，则以系统提示的方式告知 Agent：
     * 仅可使用指定数据源 ID，禁止访问其他数据源；同时附带每个数据源的名称/描述，
     * 让 LLM 在不调用 list_datasources 的情况下也能直接选择正确的数据源。
     * <p>
     * 工具侧的 {@code DatasourceQueryTool} 会从 {@link DataAgentChatScopeContext} 拿到
     * 同一份白名单做兜底校验，即便 LLM 未严格遵循提示词，也无法越权访问其他数据源。
     *
     * @param originalMessage 用户原始消息
     * @param datasourceIds   用户勾选的数据源白名单
     * @return 注入提示后的消息文本，未配置白名单时直接返回原文
     */
    private String decorateMessageWithScope(String originalMessage, List<Long> datasourceIds) {
        List<DatasourceVO> allDatasources;
        try {
            allDatasources = datasourceManageService.listDatasources();
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to load datasources for scope hint: {}", e.getMessage());
            allDatasources = List.of();
        }

        boolean hasScope = datasourceIds != null && !datasourceIds.isEmpty();
        List<DatasourceVO> targetDatasources;
        if (hasScope) {
            Set<Long> idSet = Set.copyOf(datasourceIds);
            targetDatasources = allDatasources.stream()
                    .filter(ds -> ds.getId() != null && idSet.contains(ds.getId()))
                    .collect(Collectors.toList());
        } else {
            // 用户未勾选数据源时，列出所有可用数据源，让 LLM 知道正确的 datasourceId
            targetDatasources = allDatasources;
        }

        StringBuilder hint = new StringBuilder();
        if (hasScope) {
            hint.append("[系统约束-数据源范围]\n");
            hint.append("用户已经在前端勾选了如下数据源，请严格遵守以下规则：\n");
            hint.append("1) 仅允许在以下白名单内的数据源上执行 list_tables / search_schema / execute_sql；\n");
            hint.append("2) 禁止调用 list_datasources，也不要访问白名单外的任何 datasourceId；\n");
            hint.append("3) 当用户问题与白名单数据源不匹配时，请直接说明无法回答，并提示用户调整勾选。\n\n");
            hint.append("白名单数据源：\n");
        } else {
            hint.append("[系统提示-数据源信息]\n");
            hint.append("当前可用的数据源列表如下，请根据用户问题选择合适的数据源，使用对应的 datasourceId 调用工具：\n\n");
        }

        if (targetDatasources.isEmpty() && hasScope) {
            for (Long id : datasourceIds) {
                hint.append("- datasourceId=").append(id).append('\n');
            }
        } else {
            for (DatasourceVO ds : targetDatasources) {
                hint.append("- datasourceId=").append(ds.getId())
                        .append(", name=").append(safe(ds.getName()))
                        .append(", type=").append(safe(ds.getSourceType()));
                if (ds.getDescription() != null && !ds.getDescription().isBlank()) {
                    hint.append(", description=").append(ds.getDescription());
                }
                hint.append('\n');
            }
        }

        hint.append("\n[用户问题]\n").append(originalMessage);

        // 注入业务术语预查结果，帮助 LLM 更准确地理解用户意图和映射术语
        appendBusinessTermHints(hint, originalMessage);

        return hint.toString();
    }

    /**
     * 基于用户问题执行业务术语预查，将匹配结果注入 Prompt。
     * <p>
     * 对用户原始问题做同步术语检索，将命中术语的标准名、同义词、口径等信息
     * 以提示形式注入上下文，让 Agent 在后续调用 search_business_term 时有更精准的起点。
     * <p>
     * 此步骤是纯检索（非 LLM 调用），延迟可控在 50-100ms，失败时不影响主流程。
     *
     * @param hint  已构建的提示词 StringBuilder
     * @param query 用户原始问题
     */
    private void appendBusinessTermHints(StringBuilder hint, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        try {
            BusinessTermSearchResult result = businessTermEsService.hybridSearch(
                    query,
                    DataAgentConstants.BUSINESS_TERM_SEARCH_DEFAULT_TOP_K,
                    DataAgentConstants.BUSINESS_TERM_SEARCH_DEFAULT_THRESHOLD);

            List<BusinessTermSearchResult.TermHit> hits = result.getTermHits();
            if (hits == null || hits.isEmpty()) {
                return;
            }

            hint.append("\n[系统预检索-业务术语]\n");
            hint.append("以下是从业务术语库中检索到的与用户问题相关的术语，请参考这些信息理解用户意图：\n");
            for (BusinessTermSearchResult.TermHit hit : hits) {
                hint.append("- 术语\"").append(hit.getTermName()).append("\"");
                if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
                    hint.append("（同义词: ").append(hit.getSynonyms()).append("）");
                }
                if (hit.getDataCaliber() != null && !hit.getDataCaliber().isBlank()) {
                    hint.append("，口径: ").append(hit.getDataCaliber());
                } else if (hit.getDescription() != null && !hit.getDescription().isBlank()) {
                    hint.append("，定义: ").append(hit.getDescription());
                }
                if (hit.getCategory() != null && !hit.getCategory().isBlank()) {
                    hint.append(" [分类: ").append(hit.getCategory()).append("]");
                }
                hint.append("\n");
            }
        } catch (Exception e) {
            log.debug("[DataAgent] 业务术语预查失败，跳过: {}", e.getMessage());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将附件信息注入到发送给 LLM 的 prompt 文本中。
     * <p>
     * 图片和视频附件由 BaseAgent.buildUserMessageInternal() 自动以 Media 形式注入到 LLM，
     * 此处不再重复提示。只对文件类附件（PDF、Word、Excel 等）注入路径信息，
     * 使 LLM 知晓用户上传了哪些文件及其本地路径，从而能调用工具读取文件内容。
     *
     * @param message 原始消息文本（可能已注入数据源白名单提示词）
     * @param parts   结构化消息内容片段，包含附件信息
     * @return 注入附件描述后的 prompt 文本
     */
    private String buildPromptText(String message, List<MessageContentPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return message != null ? message : "";
        }
        // 只收集文件类附件（图片/视频由 BaseAgent 多模态机制自动处理，不需要文本提示）
        List<String> attachmentLines = new ArrayList<>();
        for (MessageContentPart part : parts) {
            if (part == null || part.getType() == null) {
                continue;
            }
            switch (part.getType()) {
                case "file" -> attachmentLines.add("- 文件: " + safe(part.getFileName()) + "，路径: " + safe(part.getPath()));
                case "image", "video" -> { /* BaseAgent 会以 Media 形式注入，不需要文本提示 */ }
                default -> { /* text/thinking 等类型不重复注入 */ }
            }
        }
        if (attachmentLines.isEmpty()) {
            return message != null ? message : "";
        }
        StringBuilder builder = new StringBuilder();
        if (message != null && !message.isBlank()) {
            builder.append(message);
        }
        builder.append("\n\n[用户上传的附件]\n");
        builder.append("用户上传了以下附件，请使用 readFile 或 documentExtract 工具读取附件内容后再回答问题：\n");
        for (String line : attachmentLines) {
            builder.append(line).append('\n');
        }
        String result = builder.toString().trim();
        log.info("[DataAgent] buildPromptText: contentParts size={}, fileAttachmentLines={}, result length={}",
                parts.size(), attachmentLines.size(), result.length());
        return result;
    }

    /**
     * 解析 LLM 返回的推荐问题文本。
     * <p>
     * 将 LLM 返回的文本按行分割，过滤空行，去除编号前缀，最多取指定数量的推荐问题。
     *
     * @param rawText LLM 返回的原始文本
     * @return 推荐问题列表
     */
    private List<String> parseRecommendedQuestions(String rawText) {
        return Arrays.stream(rawText.split("\\r?\\n"))
                .map(String::trim)
                .map(line -> line.replaceAll("^[\\d]+[.、)）]\\s*", ""))
                .filter(line -> !line.isEmpty())
                .limit(DataAgentConstants.RECOMMENDED_QUESTION_MAX_COUNT)
                .collect(Collectors.toList());
    }

    /**
     * 构建包含推荐问题的 metadata JSON 字符串。
     * <p>
     * 在 StreamAccumulator 原有 metadata（toolCalls、segments）基础上，
     * 追加 recommendedQuestions 字段，使推荐问题随消息一起持久化，
     * 刷新页面后前端可从 metadata 中恢复推荐问题。
     *
     * @param accumulator  流式累积器
     * @param recQuestions 推荐问题列表（可为 null 或空）
     * @return metadata JSON 字符串
     */
    private String buildMetadataWithRecommendedQuestions(StreamAccumulator accumulator, List<String> recQuestions) {
        String baseJson = accumulator.toMetadataJson(objectMapper);
        if (recQuestions == null || recQuestions.isEmpty()) {
            return baseJson;
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(baseJson, new TypeReference<LinkedHashMap<String, Object>>() {});
            metadata.put("recommendedQuestions", recQuestions);
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to append recommended questions to metadata: {}", e.getMessage());
            return baseJson;
        }
    }

    /**
     * 将 String 类型的数据源 ID 列表转换为 Long 类型。
     * <p>
     * 前端通过 HTTP 请求传递的 ID 为字符串，避免 JavaScript 大数精度丢失。
     * 后端内部统一使用 Long 类型与数据源实体保持一致。
     *
     * @param ids 前端传递的 String 类型 ID 列表
     * @return Long 类型 ID 列表
     */
    private List<Long> convertToLongIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    private void handleStreamFinalize(SseEmitter emitter, AtomicBoolean emitterDone,
                                       StreamAccumulator accumulator, String conversationId,
                                       String status, Long agentId, String userMessage) {
        try {
            String assistantText = accumulator.getContent();
            List<MessageContentPart> assistantParts = accumulator.toAssistantParts();

            if (assistantText.isBlank() && assistantParts.isEmpty()) {
                if ("stopped".equals(status)) {
                    assistantText = "[已停止生成]";
                } else if ("failed".equals(status)) {
                    assistantText = "[错误] 请求异常";
                }
            }

            // 先生成推荐问题，以便持久化到 metadata
            List<String> recQuestions = null;
            try {
                String summary = assistantText.length() > DataAgentConstants.RECOMMENDED_QUESTION_SUMMARY_MAX_LENGTH
                        ? assistantText.substring(0, DataAgentConstants.RECOMMENDED_QUESTION_SUMMARY_MAX_LENGTH)
                        : assistantText;
                recQuestions = generateRecommendedQuestions(conversationId, agentId, userMessage, summary);
            } catch (Exception e) {
                log.debug("[DataAgent] Failed to generate recommended questions: {}", e.getMessage());
            }

            // 将推荐问题写入 metadata 以支持持久化（刷新页面后可恢复）
            String metadataJson = buildMetadataWithRecommendedQuestions(accumulator, recQuestions);

            MessageEntity savedAssistant = null;
            try {
                savedAssistant = conversationService.saveMessage(
                        conversationId, "assistant", assistantText, assistantParts, status,
                        accumulator.getPromptTokens(), accumulator.getCompletionTokens(),
                        accumulator.getRuntimeModelName(), accumulator.getRuntimeProviderId(),
                        metadataJson);
            } catch (Exception e) {
                log.warn("[DataAgent] Failed to save assistant message for {}: {}", conversationId, e.getMessage());
            }

            // Broadcast finalize events through tracker (buffered for reconnect)
            broadcastEvent(conversationId, "message_complete", Map.of(
                    "status", status,
                    "hasThinking", !accumulator.getThinking().isBlank(),
                    "hasContent", !assistantText.isBlank()
            ));

            int msgCount = 0;
            try {
                msgCount = conversationService.getMessageCount(conversationId);
            } catch (Exception ignored) {}

            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("conversationId", conversationId);
            donePayload.put("status", status);
            if (savedAssistant != null && savedAssistant.getId() != null) {
                donePayload.put("assistantMessageId", savedAssistant.getId());
                if (savedAssistant.getRuntimeModel() != null && !savedAssistant.getRuntimeModel().isBlank()) {
                    donePayload.put("runtimeModel", savedAssistant.getRuntimeModel());
                }
                if (savedAssistant.getRuntimeProvider() != null && !savedAssistant.getRuntimeProvider().isBlank()) {
                    donePayload.put("runtimeProvider", savedAssistant.getRuntimeProvider());
                }
            }
            if (accumulator.getPromptTokens() > 0) {
                donePayload.put("promptTokens", accumulator.getPromptTokens());
            }
            if (accumulator.getCompletionTokens() > 0) {
                donePayload.put("completionTokens", accumulator.getCompletionTokens());
            }
            donePayload.put("persisted", savedAssistant != null);
            donePayload.put("messageCount", msgCount);
            // 携带权威 segments 数据，前端用此覆盖实时流中缺失 segmentOnly 标记的 segments
            List<Map<String, Object>> finalizedSegments = accumulator.getFinalizedSegments();
            if (!finalizedSegments.isEmpty()) {
                donePayload.put("segments", finalizedSegments);
            }
            // 携带权威最终答案文本：实时流的 msg.content 累积了含中间旁白的全部广播内容，
            // 而 accumulator.getContent() 仅含最终答案（已排除 segmentOnly 旁白），与持久化一致。
            // 仅在正常完成时携带，避免覆盖前端在 message_complete 中为 stopped/failed 追加的标记。
            if ("completed".equals(status) && !assistantText.isBlank()) {
                donePayload.put("content", assistantText);
            }

            // 广播推荐问题事件
            if (recQuestions != null && !recQuestions.isEmpty()) {
                broadcastEvent(conversationId, "recommended_questions", Map.of("questions", recQuestions));
            }

            broadcastEvent(conversationId, "done", donePayload);
        } catch (Exception e) {
            log.warn("[DataAgent] Stream finalize error for {}: {}", conversationId, e.getMessage());
        } finally {
            // 清理当前线程的 UserContext，防止线程池复用导致身份串漏
            UserContextHolder.clear();
            streamTracker.clearStopRequested(conversationId);
            // Mark done in tracker — keeps RunState for 5-min reconnect window
            streamTracker.complete(conversationId);
            // 流终态时清理数据源白名单缓存，避免内存泄漏；后续问数会在 streamChat 入口重新写入
            scopeContext.clear(conversationId);
            try {
                conversationService.updateStreamStatus(conversationId, "idle");
            } catch (Exception e) {
                log.debug("[DataAgent] stream_status reset failed for {}: {}", conversationId, e.getMessage());
            }
            // DataAgentStreamTracker.broadcast() 是同步推送（锁内 emitter.send()），
            // done 事件广播后数据已刷出，无需额外延迟。直接 complete emitter。
            completeEmitterQuietly(emitter, emitterDone);
        }
    }

    /**
     * 通过 tracker 广播 SSE 事件到所有订阅者（用于所有事件类型）
     */
    private void broadcastEvent(String conversationId, String name, Object data) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            payload = "{\"message\":\"serialization_error\"}";
        }
        streamTracker.broadcast(conversationId, name, payload);
    }

    private void registerEmitterCallbacks(SseEmitter emitter, String conversationId, AtomicBoolean emitterDone) {
        emitter.onCompletion(() -> {
            log.debug("[DataAgent] SSE emitter completed: conversationId={}", conversationId);
            streamTracker.detach(conversationId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("[DataAgent] SSE emitter timeout: conversationId={}", conversationId);
            streamTracker.detach(conversationId, emitter);
            completeEmitterQuietly(emitter, emitterDone);
        });
        emitter.onError(e -> {
            if (isClientDisconnect(e)) {
                log.debug("[DataAgent] SSE client disconnected: conversationId={}", conversationId);
            } else {
                log.warn("[DataAgent] SSE emitter error: conversationId={}, cause={}", conversationId, e.getMessage());
            }
            streamTracker.detach(conversationId, emitter);
            completeEmitterQuietly(emitter, emitterDone);
        });
    }

    private void completeEmitterQuietly(SseEmitter emitter, AtomicBoolean emitterDone) {
        if (!emitterDone.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("[DataAgent] Emitter already completed: {}", e.getMessage());
        }
    }

    private boolean isClientDisconnect(Throwable e) {
        if (e instanceof IOException) return true;
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("broken pipe") || lower.contains("connection reset")
                || lower.contains("client abort") || lower.contains("closed");
    }

    /**
     * 待广播事件（锁外执行 I/O 的桥接结构）
     */
    record PendingBroadcast(String eventName, Map<String, Object> data) {}

    /**
     * 流式累积器 — 累积与广播分离
     * <p>
     * accept(delta, conversationId) 在 synchronized 锁内只做数据累积，
     * 将需要广播的事件收集到 pendingBroadcasts 列表，锁外再执行
     * JSON 序列化 + SSE 推送，避免 I/O 操作长时间持有锁导致流式推送卡顿。
     */
    final class StreamAccumulator {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final List<Map<String, Object>> toolCalls = new ArrayList<>();
        private final List<Map<String, Object>> segments = new ArrayList<>();
        private int segCounter = 0;
        private int promptTokens = 0;
        private int completionTokens = 0;
        private String runtimeModelName = "";
        private String runtimeProviderId = "";
        /** Whether markFirstTokenReceived has been called for this accumulator */
        boolean firstTokenMarked = false;

        /**
         * 接受流式增量数据，累积到内部缓冲区，并收集需要广播的事件。
         * <p>
         * 数据累积在 synchronized 锁内完成；广播事件收集到返回列表中，
         * 由调用方在锁外执行 JSON 序列化 + SSE 推送，避免 I/O 阻塞锁。
         *
         * @param delta          流式增量数据
         * @param conversationId 会话 ID，非 null 时收集广播事件；null 时仅累积（同步模式）
         * @return 需要在锁外广播的事件列表，可能为空
         */
        List<PendingBroadcast> accept(StreamDelta delta, String conversationId) {
            if (delta == null) {
                return List.of();
            }

            List<PendingBroadcast> pending = new ArrayList<>();

            synchronized (this) {
                if (delta.isEvent()) {
                    if ("_usage_final".equals(delta.eventType())) {
                        Map<String, Object> data = delta.eventData();
                        promptTokens = ((Number) data.getOrDefault("promptTokens", 0)).intValue();
                        completionTokens = ((Number) data.getOrDefault("completionTokens", 0)).intValue();
                        runtimeModelName = String.valueOf(data.getOrDefault("runtimeModelName", ""));
                        runtimeProviderId = String.valueOf(data.getOrDefault("runtimeProviderId", ""));
                        return List.of();
                    }
                    if ("phase".equals(delta.eventType())) {
                        if (conversationId != null && delta.eventData() != null) {
                            String phase = String.valueOf(delta.eventData().getOrDefault("phase", ""));
                            if (!phase.isEmpty()) {
                                streamTracker.updatePhase(conversationId, phase);
                            }
                        }
                        return List.of();
                    }
                    if ("finish_reason".equals(delta.eventType())) {
                        return List.of();
                    }
                    accumulateToolEvent(delta.eventType(), delta.eventData());
                    if (conversationId != null) {
                        pending.add(new PendingBroadcast(delta.eventType(), delta.eventData()));
                        // Track tool execution for heartbeat cadence
                        if ("tool_call_started".equals(delta.eventType()) && delta.eventData() != null) {
                            String toolName = String.valueOf(delta.eventData().getOrDefault("toolName", ""));
                            if (!toolName.isEmpty()) {
                                streamTracker.updateRunningTool(conversationId, toolName);
                            }
                        } else if ("tool_call_completed".equals(delta.eventType())) {
                            streamTracker.updateRunningTool(conversationId, null);
                        }
                    }
                    return pending;
                }

                if (delta.content() != null && !delta.content().isBlank()) {
                    if (!delta.segmentOnly()) {
                        content.append(delta.content());
                    }
                    if (conversationId != null && !delta.persistenceOnly()) {
                        pending.add(new PendingBroadcast("content_delta", Map.of("delta", delta.content())));
                    }
                    var seg = findLastRunning("content");
                    if (seg != null && sameDeltaFlavor(seg, delta)) {
                        seg.put("text", seg.getOrDefault("text", "") + delta.content());
                    } else {
                        finalizeRunningSegments("thinking", "content");
                        var s = newSegment("content", delta);
                        s.put("text", delta.content());
                        segments.add(s);
                    }
                }

                if (delta.thinking() != null && !delta.thinking().isBlank()) {
                    if (!delta.segmentOnly()) {
                        thinking.append(delta.thinking());
                    }
                    if (conversationId != null && !delta.persistenceOnly()) {
                        pending.add(new PendingBroadcast("thinking_delta", Map.of("delta", delta.thinking())));
                    }
                    var seg = findLastRunning("thinking");
                    if (seg != null && sameDeltaFlavor(seg, delta)) {
                        seg.put("thinkingText", seg.getOrDefault("thinkingText", "") + delta.thinking());
                    } else {
                        finalizeRunningSegments("thinking");
                        var s = newSegment("thinking", delta);
                        s.put("thinkingText", delta.thinking());
                        segments.add(s);
                    }
                }
            }

            return pending;
        }

        String getContent() { return content.toString().trim(); }
        String getThinking() { return thinking.toString().trim(); }
        int getPromptTokens() { return promptTokens; }
        int getCompletionTokens() { return completionTokens; }
        String getRuntimeModelName() { return runtimeModelName; }
        String getRuntimeProviderId() { return runtimeProviderId; }

        synchronized List<MessageContentPart> toAssistantParts() {
            List<MessageContentPart> parts = new ArrayList<>();
            if (!getContent().isBlank()) {
                MessageContentPart textPart = new MessageContentPart();
                textPart.setType("text");
                textPart.setText(getContent());
                parts.add(textPart);
            }
            if (!getThinking().isBlank()) {
                MessageContentPart thinkingPart = new MessageContentPart();
                thinkingPart.setType("thinking");
                thinkingPart.setText(getThinking());
                parts.add(thinkingPart);
            }
            return parts;
        }

        synchronized String toMetadataJson(ObjectMapper objectMapper) {
            finalizeRunningSegments("thinking", "content", "tool_call");
            try {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (!toolCalls.isEmpty()) {
                    metadata.put("toolCalls", toolCalls);
                }
                if (!segments.isEmpty()) {
                    metadata.put("segments", segments);
                }
                if (metadata.isEmpty()) {
                    return "{}";
                }
                return objectMapper.writeValueAsString(metadata);
            } catch (Exception e) {
                return "{}";
            }
        }

        /** 获取已 finalize 的 segments 列表（用于 done 事件携带权威 segments 给前端） */
        synchronized List<Map<String, Object>> getFinalizedSegments() {
            finalizeRunningSegments("thinking", "content", "tool_call");
            return new ArrayList<>(segments);
        }

        private void accumulateToolEvent(String eventType, Map<String, Object> data) {
            if ("tool_call_started".equals(eventType)) {
                Map<String, Object> tc = new LinkedHashMap<>();
                tc.put("toolCallId", String.valueOf(data.getOrDefault("toolCallId", "")));
                tc.put("name", data.getOrDefault("toolName", ""));
                tc.put("arguments", data.getOrDefault("arguments", ""));
                tc.put("status", "running");
                toolCalls.add(tc);

                finalizeRunningSegments("thinking", "content");
                var seg = newSegment("tool_call");
                seg.put("toolCallId", String.valueOf(data.getOrDefault("toolCallId", "")));
                seg.put("toolName", data.getOrDefault("toolName", ""));
                seg.put("toolArgs", data.getOrDefault("arguments", ""));
                segments.add(seg);
            } else if ("tool_call_completed".equals(eventType)) {
                String toolName = String.valueOf(data.getOrDefault("toolName", ""));
                String toolCallId = String.valueOf(data.getOrDefault("toolCallId", ""));
                for (int i = toolCalls.size() - 1; i >= 0; i--) {
                    Map<String, Object> tc = toolCalls.get(i);
                    boolean matches = (!toolCallId.isEmpty()
                                && toolCallId.equals(String.valueOf(tc.getOrDefault("toolCallId", ""))))
                            || (toolCallId.isEmpty()
                                && "running".equals(tc.get("status"))
                                && toolName.equals(tc.get("name")));
                    if (matches) {
                        tc.put("result", data.getOrDefault("result", ""));
                        tc.put("success", data.getOrDefault("success", true));
                        tc.put("status", "completed");
                        break;
                    }
                }
                for (int i = segments.size() - 1; i >= 0; i--) {
                    var seg = segments.get(i);
                    if (!"tool_call".equals(seg.get("type"))) continue;
                    boolean matches = (!toolCallId.isEmpty()
                                && toolCallId.equals(String.valueOf(seg.getOrDefault("toolCallId", ""))))
                            || (toolCallId.isEmpty()
                                && "running".equals(seg.get("status"))
                                && toolName.equals(seg.get("toolName")));
                    if (matches) {
                        seg.put("status", "completed");
                        seg.put("toolResult", data.getOrDefault("result", ""));
                        seg.put("toolSuccess", data.getOrDefault("success", true));
                        break;
                    }
                }
            }
        }

        private Map<String, Object> newSegment(String type) {
            return newSegment(type, null);
        }

        private Map<String, Object> newSegment(String type, StreamDelta delta) {
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("id", type.substring(0, 2) + "-" + segCounter++);
            seg.put("type", type);
            seg.put("status", "running");
            if (delta != null) {
                seg.put("segmentOnly", delta.segmentOnly());
                seg.put("persistenceOnly", delta.persistenceOnly());
            }
            return seg;
        }

        private boolean sameDeltaFlavor(Map<String, Object> seg, StreamDelta delta) {
            return Boolean.TRUE.equals(seg.get("segmentOnly")) == delta.segmentOnly()
                    && Boolean.TRUE.equals(seg.get("persistenceOnly")) == delta.persistenceOnly();
        }

        private Map<String, Object> findLastRunning(String type) {
            for (int i = segments.size() - 1; i >= 0; i--) {
                var seg = segments.get(i);
                if (type.equals(seg.get("type")) && "running".equals(seg.get("status"))) return seg;
            }
            return null;
        }

        private void finalizeRunningSegments(String... types) {
            var typeSet = java.util.Set.of(types);
            for (var seg : segments) {
                if ("running".equals(seg.get("status")) && typeSet.contains(seg.get("type"))) {
                    seg.put("status", "completed");
                }
            }
        }
    }
}
