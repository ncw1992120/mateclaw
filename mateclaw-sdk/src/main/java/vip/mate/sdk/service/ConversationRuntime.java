package vip.mate.sdk.service;

import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;

/**
 * 会话管理运行时接口
 * <p>
 * 提供对 MateClaw 会话管理模块的编程式访问，包括会话列表查询、
 * 消息历史加载、会话删除、重命名、置顶等。
 * 宿主应用通过注入此接口即可使用会话管理全部能力，
 * 无需直接依赖 mateclaw-server 内部服务实现。
 */
public interface ConversationRuntime {

    /**
     * 按应用类型列出会话
     *
     * @param appType 应用类型标识
     * @return 会话视图列表
     */
    List<ConversationVO> listConversations(String appType);

    /**
     * 获取会话消息历史
     *
     * @param conversationId 会话 ID
     * @return 消息视图列表
     */
    List<MessageVO> listMessages(String conversationId);

    /**
     * 删除会话
     *
     * @param conversationId 会话 ID
     */
    void deleteConversation(String conversationId);

    /**
     * 重命名会话
     *
     * @param conversationId 会话 ID
     * @param title          新标题
     */
    void renameConversation(String conversationId, String title);

    /**
     * 设置会话置顶状态
     *
     * @param conversationId 会话 ID
     * @param pinned         是否置顶
     */
    void setPinned(String conversationId, boolean pinned);
}
