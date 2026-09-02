package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.service.DataAgentConversationService;
import vip.mate.dataagent.service.DataAgentStreamTracker;
import vip.mate.dataagent.service.QueryStateService;
import vip.mate.exception.MateClawException;
import vip.mate.sdk.service.ConversationRuntime;
import vip.mate.workspace.conversation.vo.ContextUsageVO;
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

    /** 会话级查询基座服务（P0-2）：会话删除时联动清理，避免残留脏数据 */
    private final QueryStateService queryStateService;

    /** 流状态追踪器：内存 RunState 是流真实状态的权威来源，用于优先解析 streamStatus */
    private final DataAgentStreamTracker streamTracker;

    @Override
    public List<ConversationVO> listConversations() {
        String username = workspaceGuard.currentUsername();
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        return conversationRuntime.listConversations(username, workspaceId);
    }

    @Override
    public List<MessageVO> listMessages(String conversationId) {
        // 查询消息不报错：会话不存在或无权访问时返回空列表
        if (workspaceGuard.isCurrentAdmin()) {
            return conversationRuntime.listMessages(conversationId);
        }
        String username = workspaceGuard.currentUsername();
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        if (conversationRuntime.isConversationOwner(conversationId, username, workspaceId)) {
            return conversationRuntime.listMessages(conversationId);
        }
        return List.of();
    }

    @Override
    public void deleteConversation(String conversationId) {
        requireOwnership(conversationId);
        conversationRuntime.deleteConversation(conversationId);
        // P0-2: 联动清理该会话的查询基座，避免残留脏数据
        queryStateService.deleteByConversation(conversationId);
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

    @Override
    public ContextUsageVO getContextUsage(String conversationId) {
        // 非管理员需要校验会话归属
        if (!workspaceGuard.isCurrentAdmin()) {
            String username = workspaceGuard.currentUsername();
            Long workspaceId = workspaceGuard.currentWorkspaceId();
            if (!conversationRuntime.isConversationOwner(conversationId, username, workspaceId)) {
                boolean exists = conversationRuntime.listConversations(username, workspaceId)
                        .stream()
                        .anyMatch(c -> c.getConversationId().equals(conversationId));
                if (!exists) {
                    throw new MateClawException("err.conversation.notFound", 404,
                            "会话不存在: " + conversationId);
                }
                throw new MateClawException("err.conversation.forbidden", 403,
                        "无权访问该会话");
            }
        }
        return conversationRuntime.getContextUsage(conversationId);
    }

    @Override
    public String getStreamStatus(String conversationId) {
        // 非管理员需要校验会话归属（与 getContextUsage 一致）
        if (!workspaceGuard.isCurrentAdmin()) {
            String username = workspaceGuard.currentUsername();
            Long workspaceId = workspaceGuard.currentWorkspaceId();
            if (!conversationRuntime.isConversationOwner(conversationId, username, workspaceId)) {
                boolean exists = conversationRuntime.listConversations(username, workspaceId)
                        .stream()
                        .anyMatch(c -> c.getConversationId().equals(conversationId));
                if (!exists) {
                    throw new MateClawException("err.conversation.notFound", 404,
                            "会话不存在: " + conversationId);
                }
                throw new MateClawException("err.conversation.forbidden", 403,
                        "无权访问该会话");
            }
        }
        return resolveStreamStatus(conversationId);
    }

    /**
     * 解析会话流状态：内存 RunState 优先，DB 兜底。
     * <p>
     * 内存标志（stopRequested/done）在停止/完成时同步置位、零延迟，是流的
     * 权威状态；DB 的 stream_status 为异步落库的最终一致快照，存在滞后窗口
     * （如"停止后立即刷新"场景 DB 仍残留 running，导致前端误判并从头回放
     * buffer）。当前节点无 RunState 时（应用重启后），回退到 DB 值。
     */
    private String resolveStreamStatus(String conversationId) {
        String inMemoryStatus = streamTracker.getInMemoryStatus(conversationId);
        if (inMemoryStatus != null) {
            return inMemoryStatus;
        }
        return conversationRuntime.getStreamStatus(conversationId);
    }

    /**
     * 校验当前用户是否拥有该会话
     * <p>
     * 全局管理员自动放行；否则调用 SDK 的 isConversationOwner 校验。
     * 定时任务产生的系统会话对所有登录用户可见。
     * <p>
     * 若会话不存在则抛 404；若存在但不属于当前用户则抛 403。
     *
     * @param conversationId 会话 ID
     * @throws MateClawException 当会话不存在或用户不是会话拥有者时抛出
     */
    private void requireOwnership(String conversationId) {
        if (workspaceGuard.isCurrentAdmin()) {
            return;
        }
        String username = workspaceGuard.currentUsername();
        Long workspaceId = workspaceGuard.currentWorkspaceId();
        if (conversationRuntime.isConversationOwner(conversationId, username, workspaceId)) {
            return;
        }
        // 区分"会话不存在"与"无权访问"
        boolean exists = conversationRuntime.listConversations(username, workspaceId)
                .stream()
                .anyMatch(c -> c.getConversationId().equals(conversationId));
        if (!exists) {
            throw new MateClawException("err.conversation.notFound", 404, "会话不存在: " + conversationId);
        }
        throw new MateClawException("err.conversation.forbidden", 403, "无权访问该会话");
    }
}
