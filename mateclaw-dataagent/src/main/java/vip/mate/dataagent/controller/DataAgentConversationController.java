package vip.mate.dataagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.service.DataAgentConversationService;
import vip.mate.workspace.conversation.vo.ConversationVO;
import vip.mate.workspace.conversation.vo.MessageVO;

import java.util.List;
import java.util.Map;

/**
 * DataAgent 会话管理控制器
 * <p>
 * 提供会话列表查询、消息历史加载、会话删除等接口。
 * 通过 DataAgentConversationService 调用 ConversationRuntime（mateclaw-sdk 封装），
 * 不直接依赖 mateclaw-server 内部服务实现。
 */
@RestController
@RequestMapping("/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "DataAgent 会话管理", description = "数据分析 Agent 会话管理接口")
public class DataAgentConversationController {

    private final DataAgentConversationService conversationService;

    /**
     * 获取会话列表
     */
    @GetMapping
    @Operation(summary = "获取会话列表", description = "返回当前用户的所有会话，按最后活跃时间倒序")
    public R<List<ConversationVO>> list() {
        return R.ok(conversationService.listConversations());
    }

    /**
     * 获取指定会话的消息历史
     */
    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "获取会话消息历史", description = "返回指定会话的全部消息，按创建时间正序")
    public R<List<MessageVO>> listMessages(@PathVariable String conversationId) {
        return R.ok(conversationService.listMessages(conversationId));
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

    /**
     * 重命名会话标题
     */
    @PutMapping("/{conversationId}/title")
    @Operation(summary = "重命名会话", description = "更新指定会话的标题，长度限制 1-100 字符")
    public R<Void> rename(@PathVariable String conversationId, @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        if (!conversationService.renameConversation(conversationId, title)) {
            return R.fail("标题不合法");
        }
        return R.ok();
    }

    /**
     * 置顶或取消置顶会话
     */
    @PutMapping("/{conversationId}/pin")
    @Operation(summary = "置顶或取消置顶会话", description = "更新指定会话的置顶状态，置顶会话在列表中优先展示")
    public R<Void> setPinned(@PathVariable String conversationId, @RequestBody Map<String, Boolean> body) {
        conversationService.setPinned(conversationId, Boolean.TRUE.equals(body.get("pinned")));
        return R.ok();
    }
}
