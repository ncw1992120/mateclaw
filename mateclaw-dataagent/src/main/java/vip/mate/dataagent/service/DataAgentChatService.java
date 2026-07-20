package vip.mate.dataagent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import vip.mate.workspace.conversation.model.MessageContentPart;

/**
 * DataAgent 对话服务接口
 */
public interface DataAgentChatService {

    /**
     * 流式对话（含模型覆盖和数据源白名单）。
     * <p>
     * 通过 {@code modelProvider} + {@code modelName} 将用户选择的模型 pin 到 conversation 级别，
     * AgentService 按 (agentId, modelKey) 缓存不同模型变体，避免每次对话都 updateAgent + refreshAgent。
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID
     * @param modelProvider  模型 Provider ID（可选，与 modelName 成对传入）
     * @param modelName      模型名称（可选，与 modelProvider 成对传入）
     * @param datasourceIds  数据源白名单（可选）
     * @param contentParts   结构化消息内容片段，包含附件信息（可选）
     */
    SseEmitter streamChat(Long agentId, String message, String conversationId,
                          String modelProvider, String modelName, List<String> datasourceIds,
                          List<MessageContentPart> contentParts);

    /**
     * 流式对话（从请求参数，自动处理默认值）
     *
     * @param agentId        Agent ID
     * @param message        用户消息
     * @param conversationId 会话 ID（可为null，默认为"default"）
     * @param modelProvider  模型 Provider ID（可选）
     * @param modelName      模型名称（可选）
     * @param datasourceIds  数据源白名单（可选）
     * @param reconnect      是否断线重连
     * @param lastEventId    上次事件ID（可选，默认0）
     * @param contentParts   结构化消息内容片段，包含附件信息（可选）
     * @return SSE 发射器
     */
    SseEmitter streamChatFromRequest(Long agentId, String message, String conversationId,
                                      String modelProvider, String modelName, List<String> datasourceIds,
                                      boolean reconnect, Long lastEventId,
                                      List<MessageContentPart> contentParts);

    /**
     * 断线重连：附着到已有流并回放 buffer 中 lastEventId 之后的事件
     */
    SseEmitter reconnect(String conversationId, long lastEventId);

    /**
     * 同步对话（含模型覆盖和数据源白名单）。
     */
    String chat(Long agentId, String message, String conversationId,
                String modelProvider, String modelName, List<String> datasourceIds,
                List<MessageContentPart> contentParts);

    boolean requestStop(String conversationId);

    /**
     * 基于当前对话上下文生成推荐问题
     *
     * @param conversationId   会话ID
     * @param agentId          Agent ID
     * @param userMessage      当前用户问题
     * @param assistantSummary AI回答内容摘要
     * @return 推荐问题列表
     */
    List<String> generateRecommendedQuestions(String conversationId, Long agentId, String userMessage, String assistantSummary);
}
