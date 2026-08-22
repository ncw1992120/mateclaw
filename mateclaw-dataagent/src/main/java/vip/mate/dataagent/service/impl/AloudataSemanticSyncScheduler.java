package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataSemanticSyncService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aloudata 语义层定时同步调度器（页面配置：数据源 → 定时同步）。
 * <p>
 * 调度语义：
 * <ul>
 *   <li>每 60s 扫描一次启用了「定时同步」的 Aloudata 数据源（{@code aloudata_sync_enabled=1}），
 *       匹配当前时间窗内是否有 cron 触发点，命中则触发一次 {@code fullSync}；
 *       页面配置的是 5 段 cron（分 时 日 月 周），解析时补秒段 {@code "0 "} 构成 Spring 6 段再匹配；</li>
 *   <li>外层 {@code @SchedulerLock} 保护「扫描」本身（多实例下只有一个节点执行扫描）；</li>
 *   <li>内层为每个数据源单独加 ShedLock（key={@code aloudataSemanticAutoSync-{datasourceId}}），
 *       保证跨节点同一数据源不被并发同步；{@code lockAtMostFor=PT55M} 是单次同步最长时间的兜底；</li>
 *   <li>单 JVM 内再以 {@code runningDatasourceIds} 防重入（某数据源同步耗时超过扫描周期时不重复触发）。</li>
 * </ul>
 * 配置存储：{@code dataagent_datasource.aloudata_sync_enabled / aloudata_sync_cron}（页面可配），
 * 见 V214 迁移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AloudataSemanticSyncScheduler {

    /** 外层扫描锁：保护「扫描」动作，锁最短 50s（每轮扫描窗口约 60s，防止相邻两轮交错） */
    private static final String SCAN_LOCK_NAME = "aloudataSemanticAutoSyncScan";

    /** 单数据源同步锁前缀 */
    private static final String SYNC_LOCK_PREFIX = "aloudataSemanticAutoSync-";

    /** 单次同步锁持有上限（兜底：节点崩溃后其它节点可接管；正常同步远小于此） */
    private static final Duration SYNC_LOCK_AT_MOST_FOR = Duration.ofMinutes(55);

    /** 扫描周期（毫秒），与 cron 匹配时间窗一致 */
    private static final long SCAN_INTERVAL_MS = 60_000L;

    /** 同 JVM 内正在同步的数据源 ID（防重入） */
    private final Set<Long> runningDatasourceIds = ConcurrentHashMap.newKeySet();

    /** 异步同步执行器：fullSync 可能耗时较长，不阻塞调度线程与扫描锁 */
    private static final ExecutorService SYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "dataagent-aloudata-auto-sync");
        t.setDaemon(true);
        return t;
    });

    private final DatasourceMapper datasourceMapper;
    private final AloudataSemanticSyncService aloudataSemanticSyncService;
    private final LockProvider lockProvider;

    /**
     * 定时同步扫描：每分钟检查一次是否有数据源到达 cron 触发点。
     */
    @Scheduled(fixedDelay = SCAN_INTERVAL_MS, initialDelay = 30_000L)
    @SchedulerLock(name = SCAN_LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT50S")
    public void autoSyncAloudataSemantic() {
        List<DatasourceEntity> sources;
        try {
            sources = datasourceMapper.selectList(new LambdaQueryWrapper<DatasourceEntity>()
                    .eq(DatasourceEntity::getSourceType, DataAgentConstants.SOURCE_TYPE_ALOUDATA)
                    .eq(DatasourceEntity::getEnabled, true)
                    .eq(DatasourceEntity::getAloudataSyncEnabled, true)
                    .isNotNull(DatasourceEntity::getAloudataSyncCron)
                    .select(DatasourceEntity::getId, DatasourceEntity::getAloudataSyncCron));
        } catch (Exception e) {
            log.error("[Aloudata定时同步] 查询待同步数据源失败: {}", e.getMessage());
            return;
        }
        if (sources.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minusMillis(SCAN_INTERVAL_MS);
        for (DatasourceEntity ds : sources) {
            Long datasourceId = ds.getId();
            // 单 JVM 防重入
            if (runningDatasourceIds.contains(datasourceId) || !runningDatasourceIds.add(datasourceId)) {
                continue;
            }
            try {
                if (!isDue(datasourceId, ds.getAloudataSyncCron(), windowStart, now)) {
                    continue;
                }
                // 跨节点 per-datasource 分布式锁：取到锁才真正提交同步任务
                Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                        now,
                        SYNC_LOCK_PREFIX + datasourceId,
                        SYNC_LOCK_AT_MOST_FOR,
                        Duration.ZERO));
                if (lock.isEmpty()) {
                    log.debug("[Aloudata定时同步] 数据源 {} 被其它节点持锁，跳过本轮", datasourceId);
                    continue;
                }
                SimpleLock held = lock.get();
                SYNC_EXECUTOR.submit(() -> {
                    try {
                        log.info("[Aloudata定时同步] 数据源 {} 开始定时同步", datasourceId);
                        aloudataSemanticSyncService.fullSync(datasourceId);
                    } catch (Exception e) {
                        log.error("[Aloudata定时同步] 数据源 {} 同步失败: {}", datasourceId, e.getMessage());
                    } finally {
                        try {
                            held.unlock();
                        } catch (Exception unlockEx) {
                            // 锁到 lockAtMostFor 后自动过期，释放失败仅记录
                            log.warn("[Aloudata定时同步] 数据源 {} 锁释放失败: {}", datasourceId, unlockEx.getMessage());
                        }
                        runningDatasourceIds.remove(datasourceId);
                    }
                });
            } catch (Exception e) {
                log.warn("[Aloudata定时同步] 数据源 {} 调度处理失败: {}", datasourceId, e.getMessage());
                runningDatasourceIds.remove(datasourceId);
            }
        }
    }

    /**
     * 判断 cron 表达式在当前扫描窗口 [{@code windowStart}, {@code now}] 内是否有触发点。
     */
    private boolean isDue(Long datasourceId, String cron, Instant windowStart, Instant now) {
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            // 页面配置为 5 段 cron（分 时 日 月 周），补秒段构成 Spring 6 段后解析
            CronExpression expr = CronExpression.parse("0 " + cron.trim());
            // 必须用 ZonedDateTime 计算下一触发点：CronExpression 会对 DayOfWeek 等字段做
            // 字段级运算，而 java.time.Instant 不支持这些字段（会抛
            // DateTimeException: Unsupported field: DayOfWeek）。
            // 与 Spring CronTrigger 的做法一致：先把时间转成 ZonedDateTime 再调用 next()。
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime next = expr.next(windowStart.atZone(zone));
            return next != null && !next.isAfter(now.atZone(zone));
        } catch (RuntimeException e) {
            log.warn("[Aloudata定时同步] 数据源 {} 的 cron 表达式计算失败: {}（cron={}）", datasourceId, e.getMessage(), cron);
            return false;
        }
    }
}
