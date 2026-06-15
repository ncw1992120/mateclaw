package vip.mate.sdk.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.sdk.service.ConversationRuntime;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;

/**
 * 会话管理运行时实现
 * <p>
 * 将所有方法委托给 MateClaw 会话管理内部服务实现，
 * 为宿主应用提供统一的编程式访问入口。
 */
@Service
@RequiredArgsConstructor
public class ConversationRuntimeImpl implements ConversationRuntime {

    private final ConversationService conversationService;

    @Override
    public List<ConversationVO> listConversations(String appType) {
        return conversationService.listConversations(appType);
    }

    @Override
    public List<MessageVO> listMessages(String conversationId) {
        return conversationService.listMessageViews(conversationId);
    }

    @Override
    public void deleteConversation(String conversationId) {
        conversationService.deleteConversation(conversationId);
    }

    @Override
    public void renameConversation(String conversationId, String title) {
        conversationService.renameConversation(conversationId, title);
    }

    @Override
    public void setPinned(String conversationId, boolean pinned) {
        conversationService.setPinned(conversationId, pinned);
    }
}
