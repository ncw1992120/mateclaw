package vip.mate.dataagent.service;

import vip.mate.workspace.conversation.vo.ContextUsageVO;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;

/**
 * 会话管理服务接口
 */
public interface DataAgentConversationService {

    /**
     * 获取会话列表
     *
     * @return 会话列表
     */
    List<ConversationVO> listConversations();

    /**
     * 获取会话消息历史
     *
     * @param conversationId 会话 ID
     * @return 消息列表
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
     * @return 是否成功
     */
    boolean renameConversation(String conversationId, String title);

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
     * 解析顺序：内存 RunState（权威，零延迟）优先；当前节点无 RunState 时
     * （如应用重启后）回退到 DB 的 stream_status。
     *
     * @param conversationId 会话 ID
     * @return "running" / "stopped" / "idle"（stopped 表示用户主动停止后的短暂中间态）；
     *         会话不存在时返回 null
     */
    String getStreamStatus(String conversationId);
}
