package vip.mate.dataagent.service.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.service.DatasourceConnectionPoolService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 HikariCP 的数据源查询连接池实现。
 * <p>
 * 替代 {@link java.sql.DriverManager#getConnection(String, String, String)} 直连方式，
 * 通过连接池显著降低只读 SQL 查询的建连开销。
 * <p>
 * 连接池按 (jdbcUrl, username, password) 三元组独立维护：
 * <ul>
 *   <li>key 包含 password 哈希，账号密码变更会自然产生新 key 触发新池创建</li>
 *   <li>旧池通过 {@link #cleanupIdlePools()} 定时清理，避免内存泄漏</li>
 *   <li>所有连接强制只读，防误写</li>
 * </ul>
 */
@Slf4j
@Service
public class DatasourceConnectionPoolServiceImpl implements DatasourceConnectionPoolService {

    /** 连接池 key 分隔符 */
    private static final String KEY_SEPARATOR = "::";
    /** 单数据源查询账号最大连接数 */
    private static final int MAX_POOL_SIZE = 3;
    /** 最小空闲连接数 */
    private static final int MIN_IDLE = 1;
    /** 连接获取超时（毫秒） */
    private static final long CONNECTION_TIMEOUT_MS = 5_000L;
    /** 空闲连接超时（毫秒），10 分钟 */
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000L;
    /** 连接最大生命周期（毫秒），30 分钟 */
    private static final long MAX_LIFETIME_MS = 30 * 60 * 1000L;
    /** 连接池空闲清理阈值（毫秒），30 分钟未被使用的池将被关闭 */
    private static final long POOL_IDLE_EVICT_MS = 30 * 60 * 1000L;
    /** 清理线程扫描间隔（毫秒），5 分钟 */
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, PoolEntry> pools = new ConcurrentHashMap<>();

    @Override
    public Connection getReadOnlyConnection(String jdbcUrl, String username, String password) throws SQLException {
        String key = buildKey(jdbcUrl, username, password);
        PoolEntry entry = pools.computeIfAbsent(key, k -> createPool(jdbcUrl, username, password));
        entry.lastUsedAt = System.currentTimeMillis();
        Connection conn = entry.dataSource.getConnection();
        // 强制只读，防止误写
        try {
            conn.setReadOnly(true);
        } catch (SQLException ignored) {
            // 部分驱动在连接已激活后不支持 setReadOnly，忽略
        }
        return conn;
    }

    /**
     * 定时清理长时间未使用的连接池，避免内存泄漏。
     * <p>
     * 每 5 分钟扫描一次，关闭 30 分钟未被访问的池。
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupIdlePools() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PoolEntry>> it = pools.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PoolEntry> e = it.next();
            PoolEntry entry = e.getValue();
            if (now - entry.lastUsedAt > POOL_IDLE_EVICT_MS) {
                try {
                    entry.dataSource.close();
                    log.info("[DatasourcePool] Closed idle pool: {}", e.getKey());
                } catch (Exception ex) {
                    log.warn("[DatasourcePool] Failed to close idle pool {}: {}", e.getKey(), ex.getMessage());
                }
                it.remove();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        int count = pools.size();
        for (PoolEntry entry : pools.values()) {
            try {
                entry.dataSource.close();
            } catch (Exception ex) {
                log.warn("[DatasourcePool] Failed to close pool on shutdown: {}", ex.getMessage());
            }
        }
        pools.clear();
        if (count > 0) {
            log.info("[DatasourcePool] Closed {} pool(s) on shutdown", count);
        }
    }

    // ===== private 方法置于 public 方法之后 =====

    /**
     * 创建新的 HikariCP 连接池。
     */
    private PoolEntry createPool(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        if (username != null) {
            config.setUsername(username);
        }
        if (password != null) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setReadOnly(true);
        config.setPoolName("dataagent-query-pool");
        HikariDataSource ds = new HikariDataSource(config);
        log.info("[DatasourcePool] Created pool for jdbcUrl={}, user={}", jdbcUrl, username);
        return new PoolEntry(ds);
    }

    /**
     * 构建连接池缓存 key。
     * <p>
     * 包含 password 哈希：账号密码变更会自然产生新 key，
     * 触发新池创建，旧池由 {@link #cleanupIdlePools()} 异步关闭。
     */
    private static String buildKey(String jdbcUrl, String username, String password) {
        int passwordHash = (password == null) ? 0 : password.hashCode();
        return jdbcUrl + KEY_SEPARATOR + (username == null ? "" : username) + KEY_SEPARATOR + Integer.toHexString(passwordHash);
    }

    /**
     * 连接池条目：持有 HikariDataSource 与最近使用时间戳。
     */
    private static final class PoolEntry {
        final HikariDataSource dataSource;
        volatile long lastUsedAt;

        PoolEntry(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.lastUsedAt = System.currentTimeMillis();
        }
    }
}
