package vip.mate.dataagent.util;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.model.DatasourceEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcUtilsTest {

    @Test
    void buildsClickHouseUrlForHttpJdbcDriver() {
        assertEquals("jdbc:clickhouse://warehouse:8123/analytics", jdbcUrl("clickhouse", 8123, "analytics"));
    }

    @Test
    void buildsDorisUrlUsingMysqlProtocol() {
        assertEquals("jdbc:mysql://warehouse:9030/analytics?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai",
                jdbcUrl("doris", 9030, "analytics"));
    }

    @Test
    void buildsSqlServerUrlUsingDatabaseNameProperty() {
        assertEquals("jdbc:sqlserver://warehouse:1433;databaseName=analytics",
                jdbcUrl("sqlserver", 1433, "analytics"));
    }

    @Test
    void buildsStarRocksUrlUsingNativeDriverAndDefaultCatalog() {
        assertEquals("jdbc:starrocks://warehouse:9030/default_catalog.analytics",
                jdbcUrl("starrocks", 9030, "analytics"));
    }

    private String jdbcUrl(String sourceType, int port, String database) {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setSourceType(sourceType);
        entity.setHost("warehouse");
        entity.setPort(port);
        entity.setDatabaseName(database);
        return JdbcUtils.buildJdbcUrl(entity);
    }
}
