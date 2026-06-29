package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.service.DataAgentConversationService;
import vip.mate.exception.MateClawException;
import vip.mate.sdk.service.ConversationRuntime;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;

/**
 * 会话管理服务实现
 * <p>
 * 所有查询与操作均基于当前登录用户身份进行隔离：
 * <ul>
 *   <li>会话列表按 username + workspaceId 过滤</li>
 *   <li>消息历史、删除、重命名、置顶等操作前校验会话归属</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DataAgentConversationServiceImpl implements DataAgentConversationService {

    private static final int TITLE_MIN_LENGTH = 1;

    private static final int TITLE_MAX_LENGTH = 100;

    private final ConversationRuntime conversationRuntime;

    private final WorkspaceGuard workspaceGuard;

    @Override
    public List<ConversationVO> listConversations() {
        String username = workspaceGuard.currentUsername();
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        return conversationRuntime.listConversations(username, workspaceId);
    }

    @Override
    public List<MessageVO> listMessages(String conversationId) {
        requireOwnership(conversationId);
        return conversationRuntime.listMessages(conversationId);
    }

    @Override
    public void deleteConversation(String conversationId) {
        requireOwnership(conversationId);
        conversationRuntime.deleteConversation(conversationId);
    }

    @Override
    public boolean renameConversation(String conversationId, String title) {
        if (title == null || title.trim().length() < TITLE_MIN_LENGTH || title.length() > TITLE_MAX_LENGTH) {
            return false;
        }
        requireOwnership(conversationId);
        conversationRuntime.renameConversation(conversationId, title.trim());
        return true;
    }

    @Override
    public void setPinned(String conversationId, boolean pinned) {
        requireOwnership(conversationId);
        conversationRuntime.setPinned(conversationId, pinned);
    }

    /**
     * 校验当前用户是否拥有该会话
     * <p>
     * 全局管理员自动放行；否则调用 SDK 的 isConversationOwner 校验。
     * 定时任务产生的系统会话对所有登录用户可见。
     *
     * @param conversationId 会话 ID
     * @throws MateClawException 当用户不是会话拥有者时抛出 403
     */
    private void requireOwnership(String conversationId) {
        if (workspaceGuard.isCurrentAdmin()) {
            return;
        }
        String username = workspaceGuard.currentUsername();
        if (!conversationRuntime.isConversationOwner(conversationId, username)) {
            throw new MateClawException("err.conversation.forbidden", 403, "无权访问该会话");
        }
    }
}
