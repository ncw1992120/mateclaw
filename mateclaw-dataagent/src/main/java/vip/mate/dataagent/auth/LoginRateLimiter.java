package vip.mate.dataagent.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限速器（内存实现，进程级）
 * <p>
 * 按"用户名 + 固定时间窗口"统计失败次数：窗口内失败达到阈值后临时拒绝该用户名的
 * 登录尝试，成功登录即清零。保护对象包括本地 BCrypt 通道与领航代验通道。
 * <p>
 * 已知边界：按用户名限速不防"换号爆破"；键表会随不同用户名增长，依赖登录成功清理
 * 与惰性裁剪控制体积——内网系统规模下可接受，公网部署需换分布式限流组件。
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
public class LoginRateLimiter {

    /** 窗口内最大失败次数 */
    private static final int MAX_FAILURES_PER_WINDOW = 10;

    /** 统计窗口时长 */
    private static final long WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();

    private final ConcurrentHashMap<String, ArrayDeque<Long>> failureLog = new ConcurrentHashMap<>();

    /**
     * 该用户名是否已被临时封锁（窗口内失败达到阈值）
     */
    public boolean isBlocked(String username) {
        ArrayDeque<Long> queue = failureLog.get(username);
        if (queue == null) {
            return false;
        }
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
                queue.pollFirst();
            }
            return queue.size() >= MAX_FAILURES_PER_WINDOW;
        }
    }

    /**
     * 记录一次登录失败（凭据类失败才调用：401；验证码流程 429 与基础设施故障不计数）
     */
    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        failureLog.compute(username, (k, queue) -> {
            if (queue == null) {
                queue = new ArrayDeque<>();
            }
            synchronized (queue) {
                queue.addLast(now);
                // 惰性裁剪：只保留窗口内记录，防止长期运行内存膨胀
                long cutoff = now - WINDOW_MILLIS;
                while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
                    queue.pollFirst();
                }
            }
            return queue;
        });
        log.warn("[Auth] login failure recorded for [{}] (threshold={}/{})",
                username, MAX_FAILURES_PER_WINDOW, WINDOW_MILLIS / 1000 + "s");
    }

    /**
     * 登录成功后清除该用户名的失败记录
     */
    public void recordSuccess(String username) {
        failureLog.remove(username);
    }
}
