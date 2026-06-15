package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService.StreamDelta;
import vip.mate.dataagent.dto.DatasourceVO;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * DataAgent 聊天服务实现
 * <p>
 * 参考 ChatController 的流式架构：
 * <ul>
 *   <li>StreamAccumulator.accept(delta, conversationId) 累积 + 广播一体化</li>
 *   <li>通过 DataAgentStreamTracker.broadcast() 广播到所有 emitter 订阅者</li>
 *   <li>使用 Utf8SseEmitter 显式声明 charset=UTF-8</li>
 *   <li>register() + attach() 生产者-消费者解耦，支持重连回放</li>
 *   <li>心跳保活防止代理/Nginx idle timeout 中断长连接</li>
 *   <li>markFirstTokenReceived 自动调整心跳频率</li>
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
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public DataAgentChatServiceImpl(MateClawRuntime runtime,
                                    ConversationService conversationService,
                                    DataAgentStreamTracker streamTracker,
                                    ObjectMapper objectMapper,
                                    DataAgentChatScopeContext scopeContext,
                                    DatasourceManageService datasourceManageService) {
        this.runtime = runtime;
        this.conversationService = conversationService;
        this.streamTracker = streamTracker;
        this.objectMapper = objectMapper;
        this.scopeContext = scopeContext;
        this.datasourceManageService = datasourceManageService;
    }

    @Override
    public SseEmitter streamChat(Long agentId, String message, String conversationId) {
        return streamChat(agentId, message, conversationId, null, null);
    }

    @Override
    public SseEmitter streamChat(Long agentId, String message, String conversationId, String modelName) {
        return streamChat(agentId, message, conversationId, modelName, null);
    }

    @Override
    public SseEmitter streamChat(Long agentId, String message, String conversationId,
                                 String modelName, List<Long> datasourceIds) {
        // 把"用户勾选数据源"信息写入会话级上下文，供 DatasourceQueryTool 在工具执行阶段读取
        scopeContext.put(conversationId, datasourceIds);

        // 注入数据源白名单提示词，并在前端展示原始 message（持久化时仍存原文）
        String llmMessage = decorateMessageWithDatasourceScope(message, datasourceIds);
        SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        // Register RunState first (creates buffer + starts heartbeat), then attach emitter
        streamTracker.register(conversationId);
        streamTracker.attach(conversationId, emitter);
        registerEmitterCallbacks(emitter, conversationId, emitterDone);

        sseExecutor.execute(() -> {
            StreamAccumulator accumulator = new StreamAccumulator();
            AtomicBoolean finalized = new AtomicBoolean(false);
            try {
                conversationService.getOrCreateConversation(conversationId, agentId, "dataagent");
                conversationService.saveMessage(conversationId, "user", message);
                conversationService.updateStreamStatus(conversationId, "running");

                // Broadcast initial events through tracker (buffered + reach all subscribers)
                broadcastEvent(conversationId, "session", Map.of("conversationId", conversationId, "agentId", agentId));
                broadcastEvent(conversationId, "message_start", Map.of("role", "assistant"));
                broadcastEvent(conversationId, "stream_started", Map.of(
                        "conversationId", conversationId,
                        "timestamp", System.currentTimeMillis()
                ));

                Flux<StreamDelta> stream = modelName != null
                        ? runtime.chatStructuredStream(agentId, llmMessage, conversationId, modelName)
                        : runtime.chatStructuredStream(agentId, llmMessage, conversationId);

                Disposable disposable = stream
                        .doOnNext(delta -> {
                            if (emitterDone.get()) {
                                return;
                            }
                            try {
                                accumulator.accept(delta, conversationId);
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
                            handleStreamFinalize(emitter, emitterDone, accumulator, conversationId, persistStatus);
                        })
                        .doOnError(e -> {
                            if (!finalized.compareAndSet(false, true)) return;
                            boolean isUserStop = e instanceof java.util.concurrent.CancellationException
                                    || (e.getCause() instanceof java.util.concurrent.CancellationException);
                            String status = isUserStop ? "stopped" : "failed";
                            log.warn("[DataAgent] Stream {} for conversation {}: {}", status, conversationId, e.getMessage());
                            handleStreamFinalize(emitter, emitterDone, accumulator, conversationId, status);
                        })
                        .doOnCancel(() -> {
                            if (!finalized.compareAndSet(false, true)) return;
                            log.info("[DataAgent] Stream cancelled for conversation {}", conversationId);
                            handleStreamFinalize(emitter, emitterDone, accumulator, conversationId, "stopped");
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
                                             String modelName, List<Long> datasourceIds,
                                             boolean reconnect, Long lastEventId) {
        String convId = conversationId != null ? conversationId : "default";
        if (reconnect) {
            return reconnect(convId, lastEventId != null ? lastEventId : 0L);
        }
        return streamChat(agentId, message, convId, modelName, datasourceIds);
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

    @Override
    public String chat(Long agentId, String message, String conversationId) {
        return chat(agentId, message, conversationId, null, null);
    }

    @Override
    public String chat(Long agentId, String message, String conversationId, String modelName) {
        return chat(agentId, message, conversationId, modelName, null);
    }

    @Override
    public String chat(Long agentId, String message, String conversationId,
                       String modelName, List<Long> datasourceIds) {
        scopeContext.put(conversationId, datasourceIds);
        try {
            conversationService.getOrCreateConversation(conversationId, agentId, "dataagent");
            conversationService.saveMessage(conversationId, "user", message);
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to save user message for conversation {}: {}", conversationId, e.getMessage());
        }

        StreamAccumulator accumulator = new StreamAccumulator();
        String llmMessage = decorateMessageWithDatasourceScope(message, datasourceIds);
        Flux<StreamDelta> stream = modelName != null
                ? runtime.chatStructuredStream(agentId, llmMessage, conversationId, modelName)
                : runtime.chatStructuredStream(agentId, llmMessage, conversationId);

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
    private String decorateMessageWithDatasourceScope(String originalMessage, List<Long> datasourceIds) {
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            return originalMessage;
        }
        Set<Long> idSet = Set.copyOf(datasourceIds);
        List<DatasourceVO> allowed;
        try {
            allowed = datasourceManageService.listDatasources().stream()
                    .filter(ds -> ds.getId() != null && idSet.contains(ds.getId()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[DataAgent] Failed to load datasources for scope hint: {}", e.getMessage());
            allowed = List.of();
        }
        StringBuilder hint = new StringBuilder();
        hint.append("[系统约束-数据源范围]\n");
        hint.append("用户已经在前端勾选了如下数据源，请严格遵守以下规则：\n");
        hint.append("1) 仅允许在以下白名单内的数据源上执行 list_tables / search_schema / execute_sql；\n");
        hint.append("2) 禁止调用 list_datasources，也不要访问白名单外的任何 datasourceId；\n");
        hint.append("3) 当用户问题与白名单数据源不匹配时，请直接说明无法回答，并提示用户调整勾选。\n\n");
        hint.append("白名单数据源：\n");
        if (allowed.isEmpty()) {
            for (Long id : datasourceIds) {
                hint.append("- datasourceId=").append(id).append('\n');
            }
        } else {
            for (DatasourceVO ds : allowed) {
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
        return hint.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void handleStreamFinalize(SseEmitter emitter, AtomicBoolean emitterDone,
                                       StreamAccumulator accumulator, String conversationId, String status) {
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

            MessageEntity savedAssistant = null;
            try {
                savedAssistant = conversationService.saveMessage(
                        conversationId, "assistant", assistantText, assistantParts, status,
                        accumulator.getPromptTokens(), accumulator.getCompletionTokens(),
                        accumulator.getRuntimeModelName(), accumulator.getRuntimeProviderId(),
                        accumulator.toMetadataJson(objectMapper));
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
            broadcastEvent(conversationId, "done", donePayload);
        } catch (Exception e) {
            log.warn("[DataAgent] Stream finalize error for {}: {}", conversationId, e.getMessage());
        } finally {
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
            // Gracefully complete emitter after a short delay for event flushing
            sseExecutor.execute(() -> {
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                completeEmitterQuietly(emitter, emitterDone);
            });
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
     * 流式累积器 — 累积数据与广播一体化
     * <p>
     * 参考 ChatController.StreamAccumulator 的设计：
     * accept(delta, conversationId) 在累积数据的同时通过 broadcastEvent() 实时推送 SSE 事件，
     * 确保 doOnNext 中只需调用一次 accumulator.accept() 即完成累积 + 广播。
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
         * 接受流式增量数据，累积到内部缓冲区，并通过 tracker 广播 SSE 事件
         *
         * @param delta          流式增量数据
         * @param conversationId 会话 ID，非 null 时广播 SSE 事件；null 时仅累积（同步模式）
         */
        synchronized void accept(StreamDelta delta, String conversationId) {
            if (delta == null) {
                return;
            }

            if (delta.isEvent()) {
                if ("_usage_final".equals(delta.eventType())) {
                    Map<String, Object> data = delta.eventData();
                    promptTokens = ((Number) data.getOrDefault("promptTokens", 0)).intValue();
                    completionTokens = ((Number) data.getOrDefault("completionTokens", 0)).intValue();
                    runtimeModelName = String.valueOf(data.getOrDefault("runtimeModelName", ""));
                    runtimeProviderId = String.valueOf(data.getOrDefault("runtimeProviderId", ""));
                    return;
                }
                if ("phase".equals(delta.eventType())) {
                    if (conversationId != null && delta.eventData() != null) {
                        String phase = String.valueOf(delta.eventData().getOrDefault("phase", ""));
                        if (!phase.isEmpty()) {
                            streamTracker.updatePhase(conversationId, phase);
                        }
                    }
                    return;
                }
                if ("finish_reason".equals(delta.eventType())) {
                    return;
                }
                accumulateToolEvent(delta.eventType(), delta.eventData());
                if (conversationId != null) {
                    try {
                        broadcastEvent(conversationId, delta.eventType(), delta.eventData());
                    } catch (Exception e) {
                        log.warn("[DataAgent] Failed to broadcast event {}: {}", delta.eventType(), e.getMessage());
                    }
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
                return;
            }

            if (delta.content() != null && !delta.content().isBlank()) {
                if (!delta.segmentOnly()) {
                    content.append(delta.content());
                }
                if (conversationId != null && !delta.persistenceOnly()) {
                    broadcastEvent(conversationId, "content_delta", Map.of("delta", delta.content()));
                }
                var seg = findLastRunning("content");
                if (seg != null) {
                    seg.put("text", seg.getOrDefault("text", "") + delta.content());
                } else {
                    finalizeRunningSegments("thinking");
                    var s = newSegment("content");
                    s.put("text", delta.content());
                    segments.add(s);
                }
            }

            if (delta.thinking() != null && !delta.thinking().isBlank()) {
                if (!delta.segmentOnly()) {
                    thinking.append(delta.thinking());
                }
                if (conversationId != null && !delta.persistenceOnly()) {
                    broadcastEvent(conversationId, "thinking_delta", Map.of("delta", delta.thinking()));
                }
                var seg = findLastRunning("thinking");
                if (seg != null) {
                    seg.put("thinkingText", seg.getOrDefault("thinkingText", "") + delta.thinking());
                } else {
                    var s = newSegment("thinking");
                    s.put("thinkingText", delta.thinking());
                    segments.add(s);
                }
            }
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
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("id", type.substring(0, 2) + "-" + segCounter++);
            seg.put("type", type);
            seg.put("status", "running");
            return seg;
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