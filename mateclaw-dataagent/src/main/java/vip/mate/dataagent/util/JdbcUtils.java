package vip.mate.dataagent.util;

import vip.mate.dataagent.model.DatasourceEntity;

/**
 * JDBC 工具类
 * <p>
 * 提供数据源相关的 JDBC URL 构建和 SQL 标识符引用功能。
 */
public final class JdbcUtils {

    private JdbcUtils() {
    }

    /**
     * 构建 JDBC URL
     *
     * @param entity 数据源实体
     * @return JDBC URL
     */
    public static String buildJdbcUrl(DatasourceEntity entity) {
        String sourceType = entity.getSourceType();
        String host = entity.getHost();
        Integer port = entity.getPort();
        String databaseName = entity.getDatabaseName();
        String extraParams = entity.getConnectionParams();
        StringBuilder url = new StringBuilder();
        switch (sourceType) {
            case "mysql":
                url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                } else {
                    url.append("?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai");
                }
                break;
            case "postgresql":
                url.append("jdbc:postgresql://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
            case "oracle":
                url.append("jdbc:oracle:thin:@").append(host).append(":").append(port).append(":").append(databaseName);
                break;
            case "clickhouse":
                url.append("jdbc:clickhouse://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
            case "doris":
                url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;
            default:
                url.append("jdbc:").append(sourceType).append("://").append(host).append(":").append(port).append("/").append(databaseName);
                break;
        }
        return url.toString();
    }

    /**
     * 根据数据源类型对标识符加引号
     *
     * @param entity     数据源实体
     * @param identifier 标识符
     * @return 加引号后的标识符
     */
    public static String quoteIdentifier(DatasourceEntity entity, String identifier) {
        String sourceType = entity.getSourceType();
        return switch (sourceType) {
            case "mysql", "doris" -> "`" + identifier + "`";
            case "postgresql" -> "\"" + identifier + "\"";
            case "oracle" -> "\"" + identifier + "\"";
            case "clickhouse" -> "`" + identifier + "`";
            default -> identifier;
        };
    }
}
