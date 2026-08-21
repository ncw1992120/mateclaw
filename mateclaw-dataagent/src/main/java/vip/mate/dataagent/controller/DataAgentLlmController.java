package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import vip.mate.common.result.R;
import vip.mate.dataagent.support.Utf8SseEmitter;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.sdk.service.llm.dto.LlmChatRequest;
import vip.mate.sdk.service.llm.dto.LlmChatResponse;

import java.io.IOException;
import java.util.Map;

/**
 * DataAgent 大模型直连控制器
 * <p>
 * 仅支持对大模型的会话调用，不做任何持久化操作（不创建会话、不保存消息、
 * 不写库），类似通过 HTTP 直接调用大模型。最小参数集：
 * <ul>
 *   <li>{@code messages}（必填）：{role, content} 消息列表</li>
 *   <li>{@code provider} / {@code model}（可选）：缺省使用默认模型</li>
 *   <li>{@code temperature} / {@code maxTokens}（可选）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v1/llm")
@RequiredArgsConstructor
@Tag(name = "大模型直连", description = "无持久化的大模型会话调用接口（类似 HTTP 直连大模型）")
public class DataAgentLlmController {

    private final MateClawRuntime runtime;

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "同步大模型直连对话", description = "直接调用大模型并返回完整回答，不产生任何持久化记录。messages 必填；provider/model 缺省时使用默认模型。")
    public R<LlmChatResponse> chat(@RequestBody LlmChatRequest req) {
        return R.ok(runtime.chatDirect(req));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式大模型直连对话", description = "通过 SSE 推送大模型内容增量（content_delta 事件），结束时发送 done 事件；不产生任何持久化记录。")
    public SseEmitter chatStream(@RequestBody LlmChatRequest req) {
        Utf8SseEmitter emitter = new Utf8SseEmitter(10 * 60 * 1000L);
        Flux<String> deltas;
        try {
            deltas = runtime.chatDirectStream(req);
        } catch (Exception e) {
            log.warn("[DataAgentLlm] stream setup failed: {}", e.getMessage());
            return errorEmitter(emitter, e.getMessage());
        }

        deltas.subscribe(
                delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("content_delta")
                                .data(Map.of("delta", delta)));
                    } catch (IOException e) {
                        log.debug("[DataAgentLlm] stream send failed: {}", e.getMessage());
                    }
                },
                err -> {
                    log.warn("[DataAgentLlm] stream error: {}", err.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("error")
                                .data(Map.of("message", err.getMessage())));
                    } catch (IOException ignored) {
                    }
                    emitter.complete();
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done")
                                .data(Map.of("status", "completed")));
                    } catch (IOException ignored) {
                    }
                    emitter.complete();
                }
        );
        return emitter;
    }

    private SseEmitter errorEmitter(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", message == null ? "unknown error" : message)));
        } catch (IOException ignored) {
        }
        emitter.complete();
        return emitter;
    }
}
