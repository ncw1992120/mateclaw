package vip.mate.dataagent.config;

import java.util.Properties;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 方言标识配置。
 * <p>
 * 为 SqlSessionFactory 注册 DatabaseIdProvider，按 JDBC 元数据中的数据库产品名
 * 生成 databaseId（mysql / postgresql / h2）。mapper XML 中同一 statement id 可
 * 通过 databaseId 属性提供多方言版本（如 aloudata 批量 upsert 的
 * ON DUPLICATE KEY UPDATE 与 ON CONFLICT 双写），运行期按实际数据库自动路由；
 * 未标注 databaseId 的语句作为兜底版本始终可用。
 */
@Configuration
public class MybatisDialectConfig {

    /** MySQL / MariaDB 对应的 databaseId 标识 */
    private static final String DB_ID_MYSQL = "mysql";

    /** PostgreSQL 对应的 databaseId 标识 */
    private static final String DB_ID_POSTGRESQL = "postgresql";

    /** H2 对应的 databaseId 标识 */
    private static final String DB_ID_H2 = "h2";

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("MySQL", DB_ID_MYSQL);
        properties.setProperty("MariaDB", DB_ID_MYSQL);
        properties.setProperty("PostgreSQL", DB_ID_POSTGRESQL);
        properties.setProperty("H2", DB_ID_H2);
        provider.setProperties(properties);
        return provider;
    }
}
