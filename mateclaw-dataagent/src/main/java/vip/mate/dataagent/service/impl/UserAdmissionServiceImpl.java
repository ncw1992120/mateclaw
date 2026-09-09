package vip.mate.dataagent.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.config.ProtectionProperties;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.service.UserAdmissionService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 用户准入控制服务实现
 * <p>
 * 进程内实现（与内网单实例部署规模匹配）：
 * <ul>
 *   <li>限频：按「场景|用户名」维度维护 Resilience4j RateLimiter（固定周期刷新），
 *       缓存由 Caffeine 驱逐（expireAfterAccess）防止长期运行内存膨胀</li>
 *   <li>对话并发：按用户名维护 SemaphoreBulkhead，限制单用户并发 SSE 流数；
 *       槽位在流终态由 {@code #releaseChatSlot} 释放，每个流严格一次</li>
 * </ul>
 * 多实例/公网部署时需替换为分布式限流实现（如 Redis），接口契约不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdmissionServiceImpl implements UserAdmissionService {

    private final ProtectionProperties protectionProperties;

    /** 限流器缓存：key = scene|username，访问后 10 分钟驱逐 */
    private final Cache<String, RateLimiter> rateLimiters = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    /** 对话并发舱壁缓存：key = username，访问后 30 分钟驱逐 */
    private final Cache<String, Bulkhead> chatBulkheads = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Override
    public void acquireChatSlot(String username) {
        ProtectionProperties.Chat chat = protectionProperties.getChat();
        // 1. 限频检查：窗口内请求数超阈值直接拒绝
        RateLimiter limiter = rateLimiters.get(
                rateKey(DataAgentConstants.RATE_LIMIT_SCENE_CHAT, username),
                k -> newRateLimiter(chat.getPerUserRateLimit(), chat.getPerUserRateWindowSeconds()));
        try {
            limiter.acquirePermission();
        } catch (RequestNotPermitted e) {
            log.warn("[Protection] 对话请求触发限频: user={}", username);
            throw new BusinessException(429, "对话请求过于频繁，请稍后再试");
        }
        // 2. 并发舱壁：单用户并发 SSE 流数超上限直接拒绝
        Bulkhead bulkhead = chatBulkheads.get(username, k -> newChatBulkhead());
        try {
            bulkhead.acquirePermission();
        } catch (BulkheadFullException e) {
            log.warn("[Protection] 对话并发达到上限: user={}, max={}",
                    username, chat.getMaxConcurrentPerUser());
            throw new BusinessException(429, "并发对话数已达上限（" + chat.getMaxConcurrentPerUser()
                    + "），请等待当前对话完成后再发起新对话");
        }
    }

    @Override
    public void releaseChatSlot(String username) {
        Bulkhead bulkhead = chatBulkheads.getIfPresent(username);
        if (bulkhead != null) {
            bulkhead.releasePermission();
        }
    }

    @Override
    public void checkHighCostAccess(String username, String scene) {
        ProtectionProperties.HighCost highCost = protectionProperties.getHighCost();
        RateLimiter limiter = rateLimiters.get(
                rateKey(scene, username),
                k -> newRateLimiter(highCost.getPerUserRateLimit(), highCost.getWindowSeconds()));
        try {
            limiter.acquirePermission();
        } catch (RequestNotPermitted e) {
            log.warn("[Protection] 高成本端点触发限频: user={}, scene={}", username, scene);
            throw new BusinessException(429, "请求过于频繁，请稍后再试");
        }
    }

    /**
     * 构造限流器缓存键：scene|username
     */
    private String rateKey(String scene, String username) {
        return scene + DataAgentConstants.RATE_LIMIT_KEY_SEPARATOR + username;
    }

    /**
     * 构造固定周期限流器：每窗口允许 limit 次，超限立即失败（不排队等待）
     */
    private RateLimiter newRateLimiter(int limit, int windowSeconds) {
        return RateLimiter.of("user-admission", RateLimiterConfig.custom()
                .limitForPeriod(Math.max(1, limit))
                .limitRefreshPeriod(Duration.ofSeconds(Math.max(1, windowSeconds)))
                .timeoutDuration(Duration.ZERO)
                .build());
    }

    /**
     * 构造对话并发舱壁：超上限立即失败（不排队等待）
     */
    private Bulkhead newChatBulkhead() {
        return Bulkhead.of("chat-concurrency", BulkheadConfig.custom()
                .maxConcurrentCalls(Math.max(1, protectionProperties.getChat().getMaxConcurrentPerUser()))
                .maxWaitDuration(Duration.ZERO)
                .build());
    }
}
