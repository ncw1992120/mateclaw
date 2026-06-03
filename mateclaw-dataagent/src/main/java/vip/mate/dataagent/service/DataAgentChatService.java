package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * DataAgent 对话服务接口
 */
public interface DataAgentChatService {

    SseEmitter streamChat(Long agentId, String message, String conversationId);

    SseEmitter streamChat(Long agentId, String message, String conversationId, String modelName);

    /**
     * 断线重连：附着到已有流并回放 buffer 中 lastEventId 之后的事件
     */
    SseEmitter reconnect(String conversationId, long lastEventId);

    String chat(Long agentId, String message, String conversationId);

    String chat(Long agentId, String message, String conversationId, String modelName);

    boolean requestStop(String conversationId);
}