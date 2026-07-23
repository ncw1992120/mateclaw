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
 * 写入，{@code handleStreamFinalize} 在流终态时清理；同时 {@link #putDatasourceIds} 会覆盖旧值，
 * 保证同一 conversationId 下后续问数能切换数据源勾选。
 * <p>
 * 这里有意做成 Spring Bean 而非 ThreadLocal：SSE 由 sseExecutor 线程池处理，
 * doOnNext / doOnComplete 与原 Controller 线程不在同一根，ThreadLocal 不可靠。
 */
@Component
public class DataAgentChatScopeContext {

    /** conversationId -> allowed datasourceId 集合（不可变） */
    private final Map<String, Set<Long>> datasourceIdScopes = new ConcurrentHashMap<>();

    /** conversationId -> 用户原始消息（用于检索时补充关键词，防止 LLM 精简丢失关键信息） */
    private final Map<String, String> originalMessages = new ConcurrentHashMap<>();

    /** originalMessages 大小上限，防止异常中断的会话泄漏导致内存膨胀 */
    private static final int ORIGINAL_MESSAGES_MAX_SIZE = 1000;

    /**
     * 写入或更新会话级数据源白名单。
     *
     * @param conversationId 会话 ID
     * @param datasourceIds  允许使用的数据源 ID 列表；为空或 null 表示清除限制
     */
    public void putDatasourceIds(String conversationId, List<Long> datasourceIds) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            datasourceIdScopes.remove(conversationId);
            return;
        }
        datasourceIdScopes.put(conversationId, Set.copyOf(datasourceIds));
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
        Set<Long> ids = datasourceIdScopes.get(conversationId);
        return ids != null ? ids : Collections.emptySet();
    }

    /**
     * 解析数据源 ID：含单值自动注入、可用列表引导。
     * <p>
     * 策略：
     * <ol>
     *   <li>白名单为空 → 不限制，返回原值</li>
     *   <li>白名单只有一个值 → 自动注入（忽略 LLM 传值），返回白名单值</li>
     *   <li>白名单有多个值：
     *     <ul>
     *       <li>LLM 传值在白名单内 → 通过</li>
     *       <li>LLM 传值不在白名单内 → 返回错误，附带可用列表</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param conversationId 会话 ID
     * @param inputId        工具调用传入的数据源 ID
     * @return 解析结果：通过时 resolvedValue 为可用值，失败时 errorMessage 非空
     */
    public ScopeResolveResult<Long> resolveDatasourceId(String conversationId, Long inputId) {
        Set<Long> allowed = getAllowedDatasourceIds(conversationId);
        if (allowed.isEmpty()) {
            return ScopeResolveResult.ok(inputId);
        }
        if (allowed.size() == 1) {
            return ScopeResolveResult.ok(allowed.iterator().next());
        }
        if (inputId != null && allowed.contains(inputId)) {
            return ScopeResolveResult.ok(inputId);
        }
        return ScopeResolveResult.fail(
                "数据源 " + inputId + " 不在用户勾选的白名单内。可用的数据源ID：" + allowed);
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
        return datasourceIdScopes.containsKey(conversationId);
    }

    /**
     * 写入用户原始消息，供 Tool 层检索时作为补充关键词。
     *
     * @param conversationId 会话 ID
     * @param message        用户原始消息；为空或 null 表示清除
     */
    public void putOriginalMessage(String conversationId, String message) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        if (message == null || message.isBlank()) {
            originalMessages.remove(conversationId);
            return;
        }
        // 超过上限时清空，防止异常中断的会话泄漏导致内存膨胀
        if (originalMessages.size() >= ORIGINAL_MESSAGES_MAX_SIZE) {
            originalMessages.clear();
        }
        originalMessages.put(conversationId, message);
    }

    /**
     * 获取用户原始消息。
     *
     * @param conversationId 会话 ID
     * @return 用户原始消息；未设置时返回 null
     */
    public String getOriginalMessage(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return originalMessages.get(conversationId);
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
        datasourceIdScopes.remove(conversationId);
        originalMessages.remove(conversationId);
    }

    /**
     * 白名单解析结果。
     *
     * @param <T> 值类型（Long 用于数据源 ID，String 用于租户编码）
     */
    public static final class ScopeResolveResult<T> {

        /** 解析后的可用值 */
        private final T resolvedValue;

        /** 错误信息；非空表示拒绝访问 */
        private final String errorMessage;

        private ScopeResolveResult(T resolvedValue, String errorMessage) {
            this.resolvedValue = resolvedValue;
            this.errorMessage = errorMessage;
        }

        /**
         * 创建通过结果。
         *
         * @param value 解析后的可用值
         * @param <T>   值类型
         * @return 通过结果
         */
        public static <T> ScopeResolveResult<T> ok(T value) {
            return new ScopeResolveResult<>(value, null);
        }

        /**
         * 创建失败结果。
         *
         * @param message 错误信息（附带可用列表）
         * @param <T>    值类型
         * @return 失败结果
         */
        public static <T> ScopeResolveResult<T> fail(String message) {
            return new ScopeResolveResult<>(null, message);
        }

        /**
         * 获取解析后的可用值。
         *
         * @return 可用值；失败时为 null
         */
        public T getResolvedValue() {
            return resolvedValue;
        }

        /**
         * 获取错误信息。
         *
         * @return 错误信息；通过时为 null
         */
        public String getErrorMessage() {
            return errorMessage;
        }

        /**
         * 是否存在错误。
         *
         * @return true 表示拒绝访问
         */
        public boolean hasError() {
            return errorMessage != null;
        }
    }
}
