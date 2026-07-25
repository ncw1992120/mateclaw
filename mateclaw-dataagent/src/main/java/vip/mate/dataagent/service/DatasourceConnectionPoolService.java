package vip.mate.dataagent.service;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据源查询连接池服务。
 * <p>
 * 为只读 SQL 查询场景提供基于 HikariCP 的连接池能力，
 * 避免每次查询都通过 {@link java.sql.DriverManager#getConnection(String, String, String)}
 * 重新建连带来 TCP + 鉴权开销。
 * <p>
 * 按 (jdbcUrl, username, password) 维度独立维护连接池：
 * <ul>
 *   <li>同一账号多次查询复用池，建连延迟从秒级降到毫秒级</li>
 *   <li>账号密码变更会触发新池创建，旧池由清理线程异步关闭</li>
 *   <li>所有连接强制只读模式，防止误写</li>
 * </ul>
 */
public interface DatasourceConnectionPoolService {

    /**
     * 从连接池获取一个只读 Connection。
     * <p>
     * 调用方必须使用 try-with-resources 归还连接到池，避免连接泄漏。
     *
     * @param jdbcUrl  JDBC 连接 URL
     * @param username 查询账号用户名
     * @param password 查询账号密码
     * @return 已设置为只读的 Connection
     * @throws SQLException 获取连接失败
     */
    Connection getReadOnlyConnection(String jdbcUrl, String username, String password) throws SQLException;
}
