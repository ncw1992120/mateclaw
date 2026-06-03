package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vip.mate.dataagent.dto.ChatRequest;
import vip.mate.dataagent.service.DataAgentChatService;

import java.util.Map;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
@Tag(name = "DataAgent 对话", description = "数据分析 Agent 对话接口")
public class DataAgentChatController {

    private final DataAgentChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话", description = "通过 SSE 推送 Agent 结构化流式响应，支持 content_delta/thinking_delta/tool_call 等命名事件。reconnect=true 时附着到已有流并回放 buffer。")
    public SseEmitter stream(@RequestBody ChatRequest req) {
        String conversationId = req.getConversationId() != null ? req.getConversationId() : "default";

        if (req.isReconnect()) {
            return chatService.reconnect(conversationId, req.getLastEventId() != null ? req.getLastEventId() : 0L);
        }

        return chatService.streamChat(req.getAgentId(), req.getMessage(), conversationId, req.getModelName());
    }

    @PostMapping
    @Operation(summary = "同步对话", description = "等待 Agent 完整回复后返回，可选指定模型名称")
    public String chat(@RequestBody ChatRequest req) {
        return chatService.chat(req.getAgentId(), req.getMessage(), req.getConversationId(), req.getModelName());
    }

    @DeleteMapping("/stream/{conversationId}")
    @Operation(summary = "停止流式生成", description = "停止指定会话的流式生成")
    public Map<String, Object> stop(@PathVariable String conversationId) {
        boolean stopped = chatService.requestStop(conversationId);
        return Map.of(
                "conversationId", conversationId,
                "stopped", stopped
        );
    }
}