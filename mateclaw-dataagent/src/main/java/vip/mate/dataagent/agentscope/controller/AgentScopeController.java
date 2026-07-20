package vip.mate.dataagent.agentscope.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import vip.mate.dataagent.agentscope.dto.AgentCallRequest;
import vip.mate.dataagent.agentscope.service.AgentScopeService;
import vip.mate.dataagent.support.Utf8SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AgentScope Agent 调用控制器
 */
@Slf4j
@RestController
@RequestMapping("/v1/agentscope")
@Tag(name = "AgentScope Agent 调用", description = "基于 AgentScope Java SDK 的 Agent 流式调用接口")
public class AgentScopeController {

    private final AgentScopeService agentScopeService;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor;

    public AgentScopeController(AgentScopeService agentScopeService, ObjectMapper objectMapper) {
        this.agentScopeService = agentScopeService;
        this.objectMapper = objectMapper;
        int maxThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        this.sseExecutor = new ThreadPoolExecutor(
                2, maxThreads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                r -> {
                    Thread t = new Thread(r, "agentscope-sse");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式调用 Agent", description = "通过 AgentScope Java SDK 流式调用 Agent，" +
            "通过 SSE 推送推理事件（reasoning / tool_result / agent_result / summary），" +
            "支持指定模型或使用后端已配置的激活模型。模型配置通过后端模型管理接口自动获取，" +
            "无需在请求中传入 API Key 等敏感信息。")
    public SseEmitter stream(@RequestBody AgentCallRequest request) {
        SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        AtomicBoolean emitterDone = new AtomicBoolean(false);

        registerEmitterCallbacks(emitter, emitterDone);

        sseExecutor.execute(() -> {
            try {
                // 发送会话开始事件
                broadcastEvent(emitter, "session", Map.of(
                        "sessionId", request.getSessionId() != null ? request.getSessionId() : "default"
                ));

                // 订阅 AgentScope 事件流
                Flux<Event> eventFlux = agentScopeService.streamCall(request);
                Disposable disposable = eventFlux
                        .doOnNext(event -> {
                            if (emitterDone.get()) {
                                return;
                            }
                            try {
                                Map<String, Object> payload = eventToPayload(event);
                                broadcastEvent(emitter, eventTypeToName(event.getType()), payload);
                            } catch (Exception e) {
                                log.warn("[AgentScope] SSE broadcast error: {}", e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            if (!emitterDone.compareAndSet(false, true)) {
                                return;
                            }
                            try {
                                broadcastEvent(emitter, "done", Map.of("status", "completed"));
                            } catch (Exception ignored) {}
                            completeEmitterQuietly(emitter, emitterDone);
                        })
                        .doOnError(e -> {
                            if (!emitterDone.compareAndSet(false, true)) {
                                return;
                            }
                            log.warn("[AgentScope] Stream error: {}", e.getMessage());
                            try {
                                broadcastEvent(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "unknown error"));
                            } catch (Exception ignored) {}
                            completeEmitterQuietly(emitter, emitterDone);
                        })
                        .subscribe();

                // 在超时/完成时取消订阅
                emitter.onCompletion(disposable::dispose);
                emitter.onTimeout(disposable::dispose);
                emitter.onError(e -> disposable.dispose());
            } catch (Exception e) {
                log.error("[AgentScope] SSE setup error: {}", e.getMessage());
                if (!emitterDone.get()) {
                    try {
                        broadcastEvent(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "setup error"));
                    } catch (Exception ignored) {}
                    completeEmitterQuietly(emitter, emitterDone);
                }
            }
        });

        return emitter;
    }

    /**
     * 将 AgentScope Event 转换为 SSE 推送的 payload
     */
    private Map<String, Object> eventToPayload(Event event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.getType().name());
        payload.put("last", event.isLast());
        if (event.getMessage() != null) {
            String content = event.getMessage().getTextContent();
            if (content != null && !content.isEmpty()) {
                payload.put("content", content);
            }
            String name = event.getMessage().getName();
            if (name != null && !name.isEmpty()) {
                payload.put("name", name);
            }
        }
        return payload;
    }

    /**
     * 将 EventType 枚举转换为 SSE 事件名称
     */
    private String eventTypeToName(EventType type) {
        return type.name().toLowerCase();
    }

    /**
     * 通过 SseEmitter 广播 SSE 事件
     */
    private void broadcastEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String payload = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception e) {
            log.debug("[AgentScope] SSE send failed for event {}: {}", eventName, e.getMessage());
        }
    }

    private void registerEmitterCallbacks(SseEmitter emitter, AtomicBoolean emitterDone) {
        emitter.onCompletion(() -> log.debug("[AgentScope] SSE emitter completed"));
        emitter.onTimeout(() -> {
            log.debug("[AgentScope] SSE emitter timeout");
            completeEmitterQuietly(emitter, emitterDone);
        });
        emitter.onError(e -> {
            log.debug("[AgentScope] SSE emitter error: {}", e.getMessage());
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
            log.debug("[AgentScope] Emitter already completed: {}", e.getMessage());
        }
    }
}
