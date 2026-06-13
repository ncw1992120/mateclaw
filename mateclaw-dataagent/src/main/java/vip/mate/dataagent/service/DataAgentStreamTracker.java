package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import vip.mate.channel.web.ChatStreamTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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
    }

    record SseEvent(long id, String name, String json) {}

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

        RunState(String conversationId) {
            this.conversationId = conversationId;
        }
    }

    private final ConcurrentHashMap<String, RunState> runs = new ConcurrentHashMap<>();

    /** ChatStreamTracker event relay 取消句柄：conversationId -> Runnable */
    private final ConcurrentHashMap<String, Runnable> relayCancellations = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dataagent-stream-heartbeat");
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
            if (state.done) {
                log.info("[DataAgentStreamTracker] Replayed {} events; emitter stays subscribed for late events: {}",
                        state.buffer.size(), conversationId);
                startHeartbeat(conversationId);
                return true;
            }
        }
        log.info("[DataAgentStreamTracker] Client attached for conversation={}, buffer size={}",
                conversationId, state.buffer.size());
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
            synchronized (state.lock) {
                long id = ++state.nextEventId;
                SseEvent ev = new SseEvent(id, eventName, jsonData);
                state.buffer.add(ev);
                if (state.buffer.size() > MAX_BUFFER_SIZE) {
                    trimBuffer(state.buffer);
                }
                Iterator<SseEmitter> it = state.subscribers.iterator();
                while (it.hasNext()) {
                    SseEmitter emitter = it.next();
                    try {
                        emitter.send(SseEmitter.event().id(String.valueOf(id)).name(eventName).data(jsonData));
                    } catch (IOException | IllegalStateException e) {
                        log.debug("[DataAgentStreamTracker] Removing dead subscriber for {}: {}", conversationId, e.getMessage());
                        it.remove();
                    }
                }
            }
            return;
        }

        if (isHeartbeat) {
            if (state == null) return;
            synchronized (state.lock) {
                Iterator<SseEmitter> it = state.subscribers.iterator();
                while (it.hasNext()) {
                    SseEmitter emitter = it.next();
                    try {
                        emitter.send(SseEmitter.event().name(eventName).data(jsonData));
                    } catch (IOException | IllegalStateException e) {
                        log.debug("[DataAgentStreamTracker] Removing dead subscriber (heartbeat) for {}: {}", conversationId, e.getMessage());
                        it.remove();
                    }
                }
            }
            return;
        }

        if (state == null || state.done) {
            return;
        }

        synchronized (state.lock) {
            long id = ++state.nextEventId;
            SseEvent event = new SseEvent(id, eventName, jsonData);
            state.buffer.add(event);
            if (state.buffer.size() > MAX_BUFFER_SIZE) {
                trimBuffer(state.buffer);
            }
            Iterator<SseEmitter> it = state.subscribers.iterator();
            while (it.hasNext()) {
                SseEmitter emitter = it.next();
                try {
                    emitter.send(SseEmitter.event().id(String.valueOf(id)).name(eventName).data(jsonData));
                } catch (IOException | IllegalStateException e) {
                    log.debug("[DataAgentStreamTracker] Removing dead subscriber for {}: {}", conversationId, e.getMessage());
                    it.remove();
                }
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
        }
    }

    /**
     * 请求停止指定会话的流式生成
     */
    public boolean requestStop(String conversationId) {
        RunState state = runs.get(conversationId);
        if (state == null || state.done) {
            return false;
        }
        boolean firstRequest = !state.stopRequested.getAndSet(true);
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
     */
    private void installRelay(String conversationId) {
        removeRelay(conversationId);
        Runnable cancelHandle = chatStreamTracker.addEventRelay(conversationId, (eventName, jsonData) -> {
            broadcast(conversationId, eventName, jsonData);
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

    // ===== Stale RunState cleanup =====

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 600_000)
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