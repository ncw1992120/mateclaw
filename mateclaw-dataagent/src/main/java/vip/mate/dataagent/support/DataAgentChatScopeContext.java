package vip.mate.dataagent.support;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 conversationId 维度缓存"用户勾选数据源白名单"的轻量上下文。
 * <p>
 * 工具执行通过 {@link vip.mate.agent.context.ChatOriginHolder} 拿到当前会话 ID，
 * 再调用 {@link #getAllowedDatasourceIds(String)} 即可在不引入 ToolContext 的情况下
 * 把"前端选择"这种与用户输入耦合度很高的状态从 Controller 透传到 Tool。
 * <p>
 * 生命周期：与 SSE 会话保持一致，{@link DataAgentChatServiceImpl#streamChat} 入口
 * 写入，{@code handleStreamFinalize} 在流终态时清理；同时 {@link #put} 会覆盖旧值，
 * 保证同一 conversationId 下后续问数能切换数据源勾选。
 * <p>
 * 这里有意做成 Spring Bean 而非 ThreadLocal：SSE 由 sseExecutor 线程池处理，
 * doOnNext / doOnComplete 与原 Controller 线程不在同一根，ThreadLocal 不可靠。
 */
@Component
public class DataAgentChatScopeContext {

    /** conversationId -> allowed datasourceId 集合（不可变） */
    private final Map<String, Set<Long>> scopes = new ConcurrentHashMap<>();

    /**
     * 写入或更新会话级数据源白名单。
     *
     * @param conversationId 会话 ID
     * @param datasourceIds  允许使用的数据源 ID 列表；为空或 null 表示清除限制
     */
    public void put(String conversationId, List<Long> datasourceIds) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            scopes.remove(conversationId);
            return;
        }
        scopes.put(conversationId, Set.copyOf(datasourceIds));
    }

    /**
     * 获取会话级数据源白名单。
     *
     * @param conversationId 会话 ID
     * @return 不可变白名单集合；未配置时返回 {@link Collections#emptySet()}
     */
    public Set<Long> getAllowedDatasourceIds(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = scopes.get(conversationId);
        return ids != null ? ids : Collections.emptySet();
    }

    /**
     * 判断会话是否设置了白名单。
     *
     * @param conversationId 会话 ID
     * @return true=已配置白名单（即使为空集也视为不存在；本类只在非空时写入）
     */
    public boolean hasScope(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        return scopes.containsKey(conversationId);
    }

    /**
     * 清理会话白名单。流终止时调用，避免内存泄漏。
     *
     * @param conversationId 会话 ID
     */
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        scopes.remove(conversationId);
    }
}
