package vip.mate.workspace.conversation;

import vip.mate.workspace.conversation.vo.ContextUsageVO;

/**
 * 上下文使用查询服务
 * <p>
 * 提供当前会话上下文窗口占用情况的查询与记录能力。
 * 实际计算由 {@link vip.mate.agent.context.ConversationWindowManager} 在对话时完成并缓存，
 * 本服务负责暴露查询入口与兜底估算。
 */
public interface ContextUsageService {

    /**
     * 获取指定会话的上下文使用情况
     *
     * @param conversationId 会话 ID
     * @return 上下文使用视图对象；无缓存时返回基于历史消息的兜底估算
     */
    ContextUsageVO getContextUsage(String conversationId);

    /**
     * 记录（更新）指定会话的上下文使用情况
     *
     * @param conversationId 会话 ID
     * @param usage          使用情况
     */
    void recordContextUsage(String conversationId, ContextUsageVO usage);
}
