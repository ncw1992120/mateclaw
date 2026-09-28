package vip.mate.dataagent.service;

/**
 * 用户准入控制服务（限流 + 并发舱壁）
 * <p>
 * 面向高成本端点（LLM 对话流、提示词优化、洞察生成等）提供单用户维度的
 * 准入控制：限频由 Resilience4j {@code RateLimiter} 实现，对话并发上限由
 * {@code SemaphoreBulkhead} 实现。超限统一抛出 {@code BusinessException(429)}，
 * 由全局异常处理器转换为标准 429 响应。
 */
public interface UserAdmissionService {

    /**
     * 对话流准入：先做单用户限频检查，再获取并发槽位
     * <p>
     * 成功获取槽位后，流终态必须调用 {@link #releaseChatSlot(String)} 释放，
     * 且每个流只释放一次（重复释放会导致并发计数被人为抬高）。
     *
     * @param username 用户名
     * @throws vip.mate.dataagent.exception.BusinessException 超限时报 429
     */
    void acquireChatSlot(String username);

    /**
     * 释放对话流并发槽位（流终态调用）
     *
     * @param username 用户名
     */
    void releaseChatSlot(String username);

    /**
     * 高成本端点（提示词优化 / 洞察生成等）单用户限频检查
     *
     * @param username 用户名
     * @param scene    限流场景（见 DataAgentConstants.RATE_LIMIT_SCENE_*）
     * @throws vip.mate.dataagent.exception.BusinessException 超限时报 429
     */
    void checkHighCostAccess(String username, String scene);
}
