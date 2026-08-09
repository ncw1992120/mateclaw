package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import vip.mate.channel.web.ChatStreamTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DataAgent 流式对话状态追踪器
 * <p>
 * 参考 ChatStreamTracker 的生产者-消费者解耦设计：
 * <ul>
 *   <li>RunState 管理订阅者、事件缓冲区、心跳，保证线程安全</li>
 *   <li>broadcast() 通过 synchronized lock 保护迭代 + 缓冲，防止并发修改</li>
 *   <li>每个 SSE 事件带 monotonic id，支持断线重连时 dedup replay</li>
 *   <li>心跳机制防止代理/Nginx idle timeout 中断长连接</li>
 *   <li>RunState 完成后保留 5 分钟，支持重连回放 done 事件</li>
 * </ul>
 */
@Slf4j
@Service
public class DataAgentStreamTracker {

    private static final int MAX_BUFFER_SIZE = 16000;

    /** 已完成的 RunState 保留时间（5 分钟） */
    private static final long DONE_RETENTION_MS = 5 * 60 * 1000;
    /** RunState 最大存活时间（30 分钟） */
    private static final long MAX_LIFETIME_MS = 30 * 60 * 1000;

    private final ObjectMapper objectMapper;
    private final ChatStreamTracker chatStreamTracker;

    @Value("${mateclaw.dataagent.stream.heartbeat.pre-token-sec:2}")
    private int heartbeatPreTokenSec = 2;

    @Value("${mateclaw.dataagent.stream.heartbeat.streaming-sec:10}")
    private int heartbeatStreamingSec = 10;

    @Value("${mateclaw.dataagent.stream.heartbeat.tool-sec:5}")
    private int heartbeatToolSec = 5;

    public DataAgentStreamTracker(ObjectMapper objectMapper, ChatStreamTracker chatStreamTracker) {
        this.objectMapper = objectMapper;
        this.chatStreamTracker = chatStreamTracker;
        // 启动 buffer trim 定时任务：每 30s 扫描并清理超限 buffer
        trimScheduler.scheduleAtFixedRate(this::trimAllBuffers, 30, 30, TimeUnit.SECONDS);
    }

    record SseEvent(long id, String name, String json) {}

    /** 一个委派事件（事件名 + 解析后的载荷），用于持久化到消息 metadata 供前端重建委派树。 */
    public record DelegationEvent(String event, Map<String, Object> data) {}

    static final class RunState {
        final String conversationId;
        final List<SseEmitter> subscribers = new ArrayList<>();
        final List<SseEvent> buffer = new ArrayList<>();
        final Object lock = new Object();
        volatile boolean done;
        long nextEventId = 0L;
        volatile Disposable disposable;
        final AtomicBoolean stopRequested = new AtomicBoolean(false);
        volatile ScheduledFuture<?> heartbeatFuture;
        volatile boolean firstTokenReceived = false;
        volatile String currentPhase = "thinking";
        volatile String runningToolName;
        final long createdAt = System.currentTimeMillis();
        volatile long lastEventAt = System.currentTimeMillis();

        /** 第一个 thinking_delta 经 relay 到达的时刻（≈ LLM 开始吐思考 T_think_start）。
         *  StreamAccumulator 收到的 thinking 是 persistOnly 整段（graph output 处理时刻 ≈ T_think_end），
         *  无法据此算准思考耗时；用此字段修正 thinking segment 的 startTime。 */
        volatile Long thinkingStartTime;
        /** 第一个 content_delta 经 relay 到达的时刻（≈ LLM 开始吐正文 T_content_start），同理用于 content segment。 */
        volatile Long contentStartTime;

        /** 异步 trim 标记：buffer 超限时置位，由 trimScheduler 异步清理 */
        volatile boolean needsTrim;

        RunState(String conversationId) {
            this.conversationId = conversationId;
        }
    }

    private final ConcurrentHashMap<String, RunState> runs = new ConcurrentHashMap<>();

    /** ChatStreamTracker event relay 取消句柄：conversationId -> Runnable */
    private final ConcurrentHashMap<String, Runnable> relayCancellations = new ConcurrentHashMap<>();

    /**
     * 委派事件累积缓存：conversationId -> 有序事件列表。
     * <p>
     * relay 从 ChatStreamTracker 转发 delegation_* 事件时，除了推 SSE，
     * 同步累积到这里。流终态时由 DataAgentChatServiceImpl 取回并持久化到消息 metadata，
     * 使刷新页面 / 重新打开对话后前端能从 metadata.delegationEvents 重建委派树。
     * <p>
     * 用 synchronized list 保证跨线程（relay 线程追加 / finalize 线程读取）的事件顺序。
     */
    private final ConcurrentHashMap<String, List<DelegationEvent>> delegationEvents = new ConcurrentHashMap<>();

    /** 心跳调度线程池：4 线程，避免单线程在高并发会话下成为心跳瓶颈 */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "dataagent-stream-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /** 异步 trim 调度器：定期扫描所有 RunState，清理超限 buffer，避免在 broadcast hot path 中持锁过长 */
    private final ScheduledExecutorService trimScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dataagent-buffer-trim");
                t.setDaemon(true);
                return t;
            });

    /**
     * 注册流状态（开始生成时调用）。幂等。
     * <p>
     * 同时在 ChatStreamTracker 上注册 dummy RunState + event relay，
     * 使 NodeStreamingChatHelper 的逐 chunk 实时广播（content_delta / thinking_delta 等）
     * 通过 relay 转发到本 tracker，DataAgent 的 SSE 连接也能收到真正的流式增量事件。
     */
    public void register(String conversationId) {
        // 清理上次流残留的 delegation 事件累积（如上次异常中断未 drain），避免新流污染
        delegationEvents.remove(conversationId);
        runs.computeIfAbsent(conversationId, RunState::new);
        RunState state = runs.get(conversationId);
        if (state != null && state.done) {
            stopHeartbeat(conversationId);
            runs.put(conversationId, new RunState(conversationId));
        } else if (state != null) {
            if (state.stopRequested.compareAndSet(true, false)) {
                log.info("[DataAgentStreamTracker] Reset stale stopRequested on register: {}", conversationId);
            }
        }
        startHeartbeat(conversationId);
        // 在 ChatStreamTracker 上注册 dummy RunState + relay
        // dummy RunState 使 ChatStreamTracker.broadcast() 的 state != null 检查通过
        // relay 将 NodeStreamingChatHelper 的实时广播转发到本 tracker
        chatStreamTracker.register(conversationId);
        installRelay(conversationId);
        log.debug("[DataAgentStreamTracker] Stream registered: {}", conversationId);
    }

    /**
     * 将 emitter 附着到运行中或刚完成的流。先回放 buffer 再加入订阅者。
     *
     * @return true 如果成功附着或重放，false 如果没有状态可恢复
     */
    public boolean attach(String conversationId, SseEmitter emitter) {
        return attach(conversationId, emitter, 0L);
    }

    /**
     * Reconnect-aware attach: 跳过 id <= lastEventId 的已送达事件。
     * <p>
     * 注意：回放与订阅必须在同一 lock 内完成以保证事件顺序——若先订阅再 lock 外回放，
     * broadcast 会把实时事件推给 emitter，与回放事件交错导致客户端 content 累积错乱。
     * 因此沿用 lock 内 send 的设计，依靠 broadcast 期间也持有 lock 实现互斥。
     */
    public boolean attach(String conversationId, SseEmitter emitter, long lastEventId) {
        RunState state = runs.get(conversationId);
        if (state == null) {
            return false;
        }
        synchronized (state.lock) {
            int replayed = 0;
            int skipped = 0;
            for (SseEvent event : state.buffer) {
                if (event.id() <= lastEventId) {
                    skipped++;
                    continue;
                }
                try {
                    emitter.send(SseEmitter.event().id(String.valueOf(event.id())).name(event.name()).data(event.json()));
                    replayed++;
                } catch (IOException | IllegalStateException e) {
                    log.warn("[DataAgentStreamTracker] Failed to replay buffer for {}: {}", conversationId, e.getMessage());
                    return false;
                }
            }
            if (lastEventId > 0 && skipped > 0) {
                log.info("[DataAgentStreamTracker] Reconnect dedup for {}: skipped {}, replayed {}",
                        conversationId, skipped, replayed);
            }
            state.subscribers.add(emitter);
            log.info("[DataAgentStreamTracker] Client attached for conversation={}, buffer size={}, replayed={}, done={}",
                    conversationId, state.buffer.size(), replayed, state.done);
            if (state.done) {
                startHeartbeat(conversationId);
                return true;
            }
        }
        return true;
    }

    /**
     * 从订阅者列表中移除指定 emitter
     */
    public void detach(String conversationId, SseEmitter emitter) {
        RunState state = runs.get(conversationId);
        if (state == null) return;
        synchronized (state.lock) {
            state.subscribers.remove(emitter);
        }
        log.debug("[DataAgentStreamTracker] Emitter detached: {} (remaining={})",
                conversationId, state.subscribers.size());
    }

    /**
     * 广播事件到所有订阅者并缓存到 buffer。
     * <p>
     * done / async_task 事件允许在 state.done=true 后广播，
     * heartbeat 事件跳过 buffer 直推，
     * 普通事件在 state.done 后丢弃。
     * <p>
     * 锁内仅做 buffer 累积 + 订阅者快照拷贝；网络 I/O（emitter.send）在锁外执行，
     * 避免慢客户端拖累同一会话的后续事件推送。
     * buffer 超限时仅置位 needsTrim 标记，由 trimScheduler 异步清理。
     */
    public void broadcast(String conversationId, String eventName, String jsonData) {
        RunState state = runs.get(conversationId);

        boolean isDone = "done".equals(eventName);
        boolean isHeartbeat = "heartbeat".equals(eventName);

        if (state != null && !isHeartbeat) {
            state.lastEventAt = System.currentTimeMillis();
        }

        if (isDone) {
            if (state == null) return;
            List<SseEmitter> snapshot;
            long id;
            synchronized (state.lock) {
                id = ++state.nextEventId;
                SseEvent ev = new SseEvent(id, eventName, jsonData);
                state.buffer.add(ev);
                if (state.buffer.size() > MAX_BUFFER_SIZE) {
                    state.needsTrim = true;
                }
                snapshot = new ArrayList<>(state.subscribers);
            }
            sendToSubscribers(conversationId, state, snapshot, id, eventName, jsonData);
            return;
        }

        if (isHeartbeat) {
            if (state == null) return;
            List<SseEmitter> snapshot;
            synchronized (state.lock) {
                snapshot = new ArrayList<>(state.subscribers);
            }
            sendToSubscribers(conversationId, state, snapshot, -1L, eventName, jsonData);
            return;
        }

        if (state == null || state.done) {
            return;
        }

        List<SseEmitter> snapshot;
        long id;
        synchronized (state.lock) {
            if (state.done) {
                return;
            }
            id = ++state.nextEventId;
            SseEvent event = new SseEvent(id, eventName, jsonData);
            state.buffer.add(event);
            if (state.buffer.size() > MAX_BUFFER_SIZE) {
                state.needsTrim = true;
            }
            snapshot = new ArrayList<>(state.subscribers);
        }
        sendToSubscribers(conversationId, state, snapshot, id, eventName, jsonData);
    }

    /**
     * 在锁外执行 SSE 推送，避免网络 I/O 阻塞同一会话的其它事件广播。
     * <p>
     * 推送失败的 emitter（客户端断连等）会先收集到 dead 列表，
     * 在所有 send 完成后短暂重入 lock 批量移除，避免与 attach 的迭代冲突。
     *
     * @param conversationId 会话 ID
     * @param state          RunState
     * @param snapshot       在锁内拷贝的订阅者快照
     * @param id             事件 ID；负值表示不携带 id（如 heartbeat）
     * @param eventName      事件名
     * @param jsonData       事件 JSON 数据
     */
    private void sendToSubscribers(String conversationId, RunState state,
                                   List<SseEmitter> snapshot, long id,
                                   String eventName, String jsonData) {
        List<SseEmitter> dead = null;
        for (SseEmitter emitter : snapshot) {
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName).data(jsonData);
                if (id >= 0) {
                    builder.id(String.valueOf(id));
                }
                emitter.send(builder);
            } catch (IOException | IllegalStateException e) {
                if (dead == null) {
                    dead = new ArrayList<>();
                }
                dead.add(emitter);
                log.debug("[DataAgentStreamTracker] Removing dead subscriber for {}: {}", conversationId, e.getMessage());
            }
        }
        if (dead != null && !dead.isEmpty()) {
            synchronized (state.lock) {
                state.subscribers.removeAll(dead);
            }
        }
    }

    /**
     * 设置 Disposable 以便后续可以取消
     */
    public void setDisposable(String conversationId, Disposable disposable) {
        RunState state = runs.get(conversationId);
        if (state != null) {
            state.disposable = disposable;
            if (state.stopRequested.get() && disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
                log.info("[DataAgentStreamTracker] Pending stop applied after disposable ready: {}", conversationId);
            }
        }
    }

    /**
     * 请求停止指定会话的流式生成。
     * <p>
     * 同时在 ChatStreamTracker 上设置停止标志，使 Agent 节点
     * （ActionNode / ReasoningNode / NodeStreamingChatHelper 等）
     * 能通过 ChatStreamTracker.isStopRequested 感知到停止信号并中止执行。
     */
    public boolean requestStop(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state == null || state.done) {
            return false;
        }
        boolean firstRequest = !state.stopRequested.getAndSet(true);
        // 同步停止标志到 ChatStreamTracker，使 Agent 图节点能感知取消
        chatStreamTracker.requestStop(conversationId);
        updateRunningTool(conversationId, null);
        Disposable d = state.disposable;
        if (d != null && !d.isDisposed()) {
            d.dispose();
            log.info("[DataAgentStreamTracker] Stream stopped via requestStop: {}", conversationId);
            return true;
        }
        return firstRequest;
    }

    public boolean isStopRequested(String conversationId) {
        RunState state = runs.get(conversationId);
        return state != null && state.stopRequested.get();
    }

    public void clearStopRequested(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state != null) {
            state.stopRequested.set(false);
        }
    }

    /**
     * 完成后标记 done，停止心跳，清理 relay 和 dummy RunState，但保留 RunState 5 分钟供重连回放。
     */
    public void complete(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state == null) {
            return;
        }
        stopHeartbeat(conversationId);
        removeRelay(conversationId);
        chatStreamTracker.complete(conversationId);
        state.done = true;
        log.debug("[DataAgentStreamTracker] Stream completed: {} (kept in map for {}ms reconnect window)",
                conversationId, DONE_RETENTION_MS);
    }

    public boolean isRunning(String conversationId) {
        RunState state = runs.get(conversationId);
        return state != null && !state.done;
    }

    /**
     * Mark that the first content/thinking token has been received.
     * Reschedules heartbeat to streaming cadence.
     */
    public void markFirstTokenReceived(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state == null) return;
        if (state.firstTokenReceived) return;
        state.firstTokenReceived = true;
        rescheduleHeartbeat(conversationId);
    }

    /**
     * 更新当前执行阶段
     */
    public void updatePhase(String conversationId, String phase) {
        RunState state = runs.get(conversationId);
        if (state != null) {
            state.currentPhase = phase;
        }
    }

    /**
     * 更新正在执行的工具名称，并自动调整心跳频率
     */
    public void updateRunningTool(String conversationId, String toolName) {
        RunState state = runs.get(conversationId);
        if (state != null) {
            String previous = state.runningToolName;
            state.runningToolName = toolName;
            boolean wasRunning = previous != null && !previous.isEmpty();
            boolean nowRunning = toolName != null && !toolName.isEmpty();
            if (wasRunning != nowRunning) {
                rescheduleHeartbeat(conversationId);
            }
        }
    }

    // ===== Heartbeat =====

    private int currentHeartbeatIntervalSec(RunState state) {
        if (state.runningToolName != null && !state.runningToolName.isEmpty()) {
            return heartbeatToolSec;
        }
        return state.firstTokenReceived ? heartbeatStreamingSec : heartbeatPreTokenSec;
    }

    public void startHeartbeat(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state == null) return;
        if (state.heartbeatFuture != null && !state.heartbeatFuture.isDone()) return;

        int intervalSec = currentHeartbeatIntervalSec(state);
        state.heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                RunState s = runs.get(conversationId);
                if (s == null) {
                    stopHeartbeat(conversationId);
                    return;
                }
                if (s.done && s.subscribers.isEmpty()) {
                    stopHeartbeat(conversationId);
                    return;
                }
                String json;
                try {
                    json = objectMapper.writeValueAsString(Map.of(
                            "conversationId", conversationId,
                            "currentPhase", safe(s.currentPhase),
                            "runningToolName", safe(s.runningToolName),
                            "timestamp", System.currentTimeMillis()
                    ));
                } catch (Exception e) {
                    json = "{\"conversationId\":\"" + conversationId + "\"}";
                }
                broadcast(conversationId, "heartbeat", json);
            } catch (Exception e) {
                log.debug("[DataAgentStreamTracker] Heartbeat error for {}: {}", conversationId, e.getMessage());
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    public void rescheduleHeartbeat(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state == null || state.done) return;
        if (state.heartbeatFuture != null) {
            state.heartbeatFuture.cancel(false);
            state.heartbeatFuture = null;
        }
        startHeartbeat(conversationId);
    }

    public void stopHeartbeat(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state != null && state.heartbeatFuture != null) {
            state.heartbeatFuture.cancel(false);
            state.heartbeatFuture = null;
        }
    }

    // ===== Event Relay =====

    /**
     * 在 ChatStreamTracker 上注册 event relay，将 NodeStreamingChatHelper 的
     * 实时广播（content_delta / thinking_delta 等）转发到本 tracker。
     * <p>
     * 前置条件：chatStreamTracker.register(conversationId) 已调用，
     * 使 ChatStreamTracker 中存在对应的 RunState（否则 broadcast() 中 state==null 会拦截事件）。
     * <p>
     * 注意：tool_call_started / tool_call_completed 事件不通过 relay 转发，
     * 因为它们已经通过 PENDING_EVENTS → StreamDelta 管道由 StreamAccumulator 广播，
     * 若 relay 再转发一次会导致前端收到重复的 tool 事件，造成工具执行情况重复展示
     * 以及思考过程未正确折叠到执行过程卡片中。
     */
    private void installRelay(String conversationId) {
        removeRelay(conversationId);
        Runnable cancelHandle = chatStreamTracker.addEventRelay(conversationId, (eventName, jsonData) -> {
            // tool 事件已通过 StreamDelta 管道推送，跳过 relay 转发以避免重复
            if ("tool_call_started".equals(eventName) || "tool_call_completed".equals(eventName)) {
                return;
            }
            broadcast(conversationId, eventName, jsonData);
            // 记录第一个 thinking_delta / content_delta 的到达时刻，
            // 供 StreamAccumulator 修正 segment 的 startTime（默认是 graph output 处理时刻，
            // 即 T_think_end/T_content_end，会导致 durationMs 包含工具执行时间）。
            // 用首个 delta 的到达时刻（≈ T_think_start/T_content_start）才能算准耗时。
            if ("thinking_delta".equals(eventName) || "content_delta".equals(eventName)) {
                RunState st = runs.get(conversationId);
                if (st != null) {
                    long now = System.currentTimeMillis();
                    if ("thinking_delta".equals(eventName) && st.thinkingStartTime == null) {
                        st.thinkingStartTime = now;
                    } else if ("content_delta".equals(eventName) && st.contentStartTime == null) {
                        st.contentStartTime = now;
                    }
                }
            }
            // 委派事件额外累积到缓存，供流终态持久化到消息 metadata，
            // 使刷新/重开对话后前端能重建委派树（实时流已构建，但后端权威 segments 不含委派数据）。
            if (eventName != null && eventName.startsWith("delegation_")) {
                appendDelegationEvent(conversationId, eventName, jsonData);
            }
        });
        relayCancellations.put(conversationId, cancelHandle);
        log.debug("[DataAgentStreamTracker] Event relay installed for {}", conversationId);
    }

    /**
     * 移除 event relay
     */
    private void removeRelay(String conversationId) {
        Runnable cancelHandle = relayCancellations.remove(conversationId);
        if (cancelHandle != null) {
            cancelHandle.run();
            log.debug("[DataAgentStreamTracker] Event relay removed for {}", conversationId);
        }
    }

    /**
     * 累积一个 delegation 事件到 per-conversation 缓存（relay 线程调用）。
     * jsonData 解析为 Map；解析失败时存原始字符串到 {@code _raw} 字段以免丢失。
     */
    private void appendDelegationEvent(String conversationId, String eventName, String jsonData) {
        List<DelegationEvent> list = delegationEvents.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()));
        Map<String, Object> data;
        try {
            data = objectMapper.readValue(jsonData, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // 解析失败兜底：保留原始 JSON 字符串，前端重建时跳过解析失败的条目
            data = new LinkedHashMap<>();
            data.put("_raw", jsonData);
            data.put("_parseError", e.getMessage());
        }
        list.add(new DelegationEvent(eventName, data));
    }

    /**
     * 取回并清空指定会话累积的 delegation 事件列表（流终态时由 chatService 调用以持久化）。
     * 返回事件的浅拷贝快照，避免 finalize 线程遍历时 relay 线程并发修改。
     */
    public List<DelegationEvent> drainDelegationEvents(String conversationId) {
        List<DelegationEvent> list = delegationEvents.remove(conversationId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    /** 第一个 thinking_delta 到达时刻（≈ T_think_start），null 表示未收到过 thinking。 */
    public Long getThinkingStartTime(String conversationId) {
        RunState st = runs.get(conversationId);
        return st != null ? st.thinkingStartTime : null;
    }

    /** 第一个 content_delta 到达时刻（≈ T_content_start），null 表示未收到过 content。 */
    public Long getContentStartTime(String conversationId) {
        RunState st = runs.get(conversationId);
        return st != null ? st.contentStartTime : null;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    // ===== Buffer trimming =====

    private static void trimBuffer(List<SseEvent> buffer) {
        if (buffer.size() <= MAX_BUFFER_SIZE) return;
        int target = buffer.size() - MAX_BUFFER_SIZE;

        Iterator<SseEvent> it = buffer.iterator();
        while (it.hasNext() && target > 0) {
            SseEvent e = it.next();
            if ("thinking_delta".equals(e.name())) {
                it.remove();
                target--;
            }
        }

        if (target > 0) {
            it = buffer.iterator();
            while (it.hasNext() && target > 0) {
                SseEvent e = it.next();
                if ("content_delta".equals(e.name())) {
                    it.remove();
                    target--;
                }
            }
        }
        log.debug("[DataAgentStreamTracker] Buffer trimmed: {} events remain", buffer.size());
    }

    /**
     * 异步扫描所有 RunState，清理超限 buffer。
     * <p>
     * 由 trimScheduler 每 30s 触发一次。trimBuffer 本身是 O(n) 扫描，
     * 在 broadcast hot path 中改为只标记 needsTrim，由本方法在锁内同步执行 trim，
     * 避免锁持有时间过长影响推送。
     */
    private void trimAllBuffers() {
        for (RunState state : runs.values()) {
            if (!state.needsTrim) {
                continue;
            }
            try {
                synchronized (state.lock) {
                    if (!state.needsTrim) {
                        continue;
                    }
                    trimBuffer(state.buffer);
                    state.needsTrim = false;
                }
            } catch (Exception e) {
                log.debug("[DataAgentStreamTracker] Buffer trim error for {}: {}",
                        state.conversationId, e.getMessage());
            }
        }
    }

    // ===== Stale RunState cleanup =====

    @Scheduled(fixedRate = 600_000)
    public void cleanupStaleRuns() {
        long now = System.currentTimeMillis();
        int evicted = 0;

        var iterator = runs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            RunState state = entry.getValue();
            long age = now - state.createdAt;

            boolean shouldEvict = false;
            String reason = null;

            if (state.done && age > DONE_RETENTION_MS) {
                shouldEvict = true;
                reason = "completed and expired";
            } else if (age > MAX_LIFETIME_MS) {
                shouldEvict = true;
                reason = "exceeded max lifetime (" + (age / 1000) + "s)";
            }

            if (shouldEvict) {
                stopHeartbeat(entry.getKey());
                removeRelay(entry.getKey());
                delegationEvents.remove(entry.getKey());
                Disposable d = state.disposable;
                if (d != null && !d.isDisposed()) {
                    d.dispose();
                }
                iterator.remove();
                evicted++;
                log.warn("[DataAgentStreamTracker] Evicted stale RunState for {}: {}", entry.getKey(), reason);
            }
        }

        if (evicted > 0) {
            log.info("[DataAgentStreamTracker] Cleanup: evicted {} stale entries, {} remaining", evicted, runs.size());
        }
    }

    @PreDestroy
    public void onShutdown() {
        // 关闭调度器，释放线程，避免容器 shutdown 时线程泄漏
        heartbeatScheduler.shutdownNow();
        trimScheduler.shutdownNow();
        int active = (int) runs.values().stream().filter(s -> !s.done).count();
        if (active == 0) {
            log.info("[DataAgentStreamTracker] Shutdown: no active runs");
            return;
        }
        log.warn("[DataAgentStreamTracker] Shutdown: {} active run(s) to terminate", active);
        for (Map.Entry<String, RunState> entry : runs.entrySet()) {
            RunState state = entry.getValue();
            if (state.done) continue;
            String cid = entry.getKey();
            try {
                Disposable d = state.disposable;
                if (d != null && !d.isDisposed()) {
                    d.dispose();
                }
            } catch (Exception e) {
                log.warn("[DataAgentStreamTracker] Disposable.dispose failed for {}: {}", cid, e.getMessage());
            }
        }
    }
}