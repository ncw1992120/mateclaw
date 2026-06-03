package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.workspace.conversation.ConversationService;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;

/**
 * DataAgent 会话管理控制器
 * <p>
 * 提供会话列表查询、消息历史加载、会话删除等接口。
 * 复用 mateclaw-server 的 ConversationService 实现持久化操作。
 */
@RestController
@RequestMapping("/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "DataAgent 会话管理", description = "数据分析 Agent 会话管理接口")
public class DataAgentConversationController {

    private final ConversationService conversationService;

    /**
     * 获取会话列表
     */
    @GetMapping
    @Operation(summary = "获取会话列表", description = "返回当前用户的所有会话，按最后活跃时间倒序")
    public R<List<ConversationVO>> list() {
        return R.ok(conversationService.listConversations("dataagent"));
    }

    /**
     * 获取指定会话的消息历史
     */
    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "获取会话消息历史", description = "返回指定会话的全部消息，按创建时间正序")
    public R<List<MessageVO>> listMessages(@PathVariable String conversationId) {
        return R.ok(conversationService.listMessageViews(conversationId));
    }

    /**
     * 删除会话（同时删除消息）
     */
    @DeleteMapping("/{conversationId}")
    @Operation(summary = "删除会话", description = "删除指定会话及其所有消息")
    public R<Void> delete(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return R.ok();
    }
}
