package vip.mate.dataagent.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long agentId;
    private String conversationId;
    private String message;
    private String modelName;
    /** Whether this is a reconnect request after stream interruption */
    private boolean reconnect;
    /** Last SSE event ID received before disconnection (for dedup replay) */
    private Long lastEventId;
}