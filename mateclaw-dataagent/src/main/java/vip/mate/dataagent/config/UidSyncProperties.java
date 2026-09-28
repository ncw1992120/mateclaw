package vip.mate.dataagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 用户 UID 映射同步配置属性
 * <p>
 * 纯配置持有类，不含业务处理逻辑。同步规则见 {@code UserUidMappingSyncServiceImpl}。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = UidSyncProperties.CONFIG_PREFIX)
public class UidSyncProperties {

    public static final String CONFIG_PREFIX = "dataagent.uid-sync";

    /** 是否启用定时同步（默认关闭，按环境开启） */
    private boolean enabled = false;

    /** 同步 cron（Spring 6 段表达式，默认每日 02:00） */
    private String cron = "0 0 2 * * *";

    /** 同步源数据库类型：mysql / postgresql（与数据源管理的 sourceType 取值一致） */
    private String sourceType;

    /** 同步源主机（source-url 留空时按 分片字段 + 类型 拼装 JDBC URL） */
    private String sourceHost;

    /**
     * 同步源端口
     * <p>
     * 声明为 String 由 {@link #resolvedSourceUrl()} 手动解析：环境变量缺省时占位符
     * 解析为空串，避免 Spring 把空串绑定到 Integer 失败导致启动报错。
     */
    private String sourcePort;

    /** 同步源库名（PG 为 database；跨 schema 查询写在拉取 SQL 的限定表名里） */
    private String sourceDatabase;

    /**
     * 源库 JDBC URL（支持 MySQL / PostgreSQL，由 URL 前缀自动识别驱动）
     * <p>
     * 高级用法：留空时按 source-type / source-host / source-port / source-database
     * 拼装默认 URL，无需手写 JDBC 连接串。
     */
    private String sourceUrl;

    /** 源库用户名 */
    private String sourceUsername;

    /** 源库密码（建议环境变量注入） */
    private String sourcePassword;

    /**
     * 解析最终生效的 JDBC URL
     * <p>
     * source-url 非空时原样使用（兼容完整连接串写法）；
     * 留空时按 source-type / source-host / source-port / source-database 拼装：
     * mysql 走 jdbc:mysql:// 并带基础参数，postgresql 走 jdbc:postgresql://，
     * 其余类型不支持分片拼装（返回 null，由调用方按未配置处理）。
     */
    public String resolvedSourceUrl() {
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            return sourceUrl.trim();
        }
        Integer port = parsePort();
        if (isBlank(sourceType) || isBlank(sourceHost) || port == null || isBlank(sourceDatabase)) {
            return null;
        }
        String type = sourceType.trim().toLowerCase(Locale.ROOT);
        String base = switch (type) {
            case "mysql" -> "jdbc:mysql://" + sourceHost.trim() + ":" + port + "/" + sourceDatabase.trim()
                    + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";
            case "postgresql" -> "jdbc:postgresql://" + sourceHost.trim() + ":" + port + "/" + sourceDatabase.trim();
            default -> null;
        };
        return base;
    }

    /**
     * 解析端口，非数字或未配置返回 null（按未配置处理，由调用方给出明确报错）
     */
    private Integer parsePort() {
        if (isBlank(sourcePort)) {
            return null;
        }
        try {
            return Integer.parseInt(sourcePort.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 同步源配置是否完整可用
     */
    public boolean isSourceConfigured() {
        return resolvedSourceUrl() != null
                && sourceUsername != null && !sourceUsername.isBlank()
                && querySql != null && !querySql.isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 拉取 SQL，必须返回三列（列名或 {@code as} 别名均可）：
     * username（登录名）/ tenant_id（租户）/ aloudata_uid（UID）
     * <p>
     * 取值按列名匹配（大小写不敏感、列序不限），缺列时同步显式失败并回显实际列名；
     * 可选返回 nickname（昵称）列，缺失或为空时回落 mate_user.nickname。
     */
    private String querySql;

    /** 单批 upsert 大小 */
    private int batchSize = 500;
}
