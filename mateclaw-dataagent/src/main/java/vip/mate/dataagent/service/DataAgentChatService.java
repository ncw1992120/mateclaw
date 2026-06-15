package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * DataAgent 对话服务接口
 */
public interface DataAgentChatService {

    SseEmitter streamChat(Long agentId, String message, String conversationId);

    SseEmitter streamChat(Long agentId, String message, String conversationId, String modelName);

    /**
     * 流式对话（含数据源白名单）。
     * <p>
     * 当 {@code datasourceIds} 非空时，后端会在用户消息前注入"仅允许使用以下数据源"
     * 的提示词，并把白名单写入 {@link vip.mate.dataagent.support.DataAgentChatScopeContext}，
     * 由 {@code DatasourceQueryTool} 做兜底校验。
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @param modelName      模型名称（可选）
     * @param datasourceIds  用户勾选的数据源白名单；null/空表示不限制
     */
    SseEmitter streamChat(Long agentId, String message, String conversationId,
                          String modelName, List<Long> datasourceIds);

    /**
     * 流式对话（从请求参数，自动处理默认值）
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID（可为null，默认为"default"）
     * @param modelName      模型名称（可选）
     * @param datasourceIds  数据源白名单（可选）
     * @param reconnect      是否断线重连
     * @param lastEventId    上次事件ID（可选，默认0）
     * @return SSE 发射器
     */
    SseEmitter streamChatFromRequest(Long agentId, String message, String conversationId,
                                      String modelName, List<Long> datasourceIds,
                                      boolean reconnect, Long lastEventId);

    /**
     * 断线重连：附着到已有流并回放 buffer 中 lastEventId 之后的事件
     */
    SseEmitter reconnect(String conversationId, long lastEventId);

    String chat(Long agentId, String message, String conversationId);

    String chat(Long agentId, String message, String conversationId, String modelName);

    /**
     * 同步对话（含数据源白名单）。
     */
    String chat(Long agentId, String message, String conversationId,
                String modelName, List<Long> datasourceIds);

    boolean requestStop(String conversationId);
}
