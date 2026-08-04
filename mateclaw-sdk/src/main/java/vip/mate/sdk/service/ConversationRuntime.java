package vip.mate.sdk.service;

import vip.mate.workspace.conversation.vo.ContextUsageVO;
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
     * 按用户名和工作区列出会话
     * <p>
     * 返回该用户创建的会话以及系统（定时任务）产生的会话，
     * 仅限指定工作区内，按置顶与最后活跃时间倒序。
     *
     * @param username    用户名
     * @param workspaceId 工作区 ID
     * @return 会话视图列表
     */
    List<ConversationVO> listConversations(String username, Long workspaceId);

    /**
     * 校验用户是否拥有该会话
     * <p>
     * 定时任务产生的会话（username = system）对所有登录用户可见。
     *
     * @param conversationId 会话 ID
     * @param username       用户名
     * @return true 如果用户是会话拥有者或会话为系统会话
     */
    boolean isConversationOwner(String conversationId, String username);

    /**
     * 校验用户是否拥有该会话，并校验会话所属工作区
     * <p>
     * 定时任务产生的会话（username = system）对所有登录用户可见。
     * 同时校验会话的 workspaceId 与传入的 workspaceId 一致，防止跨工作区越权访问。
     *
     * @param conversationId 会话 ID
     * @param username       用户名
     * @param workspaceId    工作区 ID
     * @return true 如果用户是会话拥有者或会话为系统会话，且会话属于指定工作区
     */
    boolean isConversationOwner(String conversationId, String username, Long workspaceId);

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

    /**
     * 获取会话上下文使用情况
     *
     * @param conversationId 会话 ID
     * @return 上下文使用视图对象
     */
    ContextUsageVO getContextUsage(String conversationId);

    /**
     * 获取会话实时流状态
     * <p>
     * 用于页面刷新后判断是否需要 reconnect 接入仍在运行的流。
     *
     * @param conversationId 会话 ID
     * @return "running" / "idle"；会话不存在时返回 null
     */
    String getStreamStatus(String conversationId);
}
