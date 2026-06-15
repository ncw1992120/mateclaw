package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.service.DataAgentConversationService;
import vip.mate.sdk.service.ConversationRuntime;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;

/**
 * 会话管理服务实现
 */
@Service
@RequiredArgsConstructor
public class DataAgentConversationServiceImpl implements DataAgentConversationService {

    private static final String APP_TYPE = "dataagent";

    private static final int TITLE_MIN_LENGTH = 1;

    private static final int TITLE_MAX_LENGTH = 100;

    private final ConversationRuntime conversationRuntime;

    @Override
    public List<ConversationVO> listConversations() {
        return conversationRuntime.listConversations(APP_TYPE);
    }

    @Override
    public List<MessageVO> listMessages(String conversationId) {
        return conversationRuntime.listMessages(conversationId);
    }

    @Override
    public void deleteConversation(String conversationId) {
        conversationRuntime.deleteConversation(conversationId);
    }

    @Override
    public boolean renameConversation(String conversationId, String title) {
        if (title == null || title.trim().isEmpty() || title.length() > TITLE_MAX_LENGTH) {
            return false;
        }
        conversationRuntime.renameConversation(conversationId, title.trim());
        return true;
    }

    @Override
    public void setPinned(String conversationId, boolean pinned) {
        conversationRuntime.setPinned(conversationId, pinned);
    }
}
