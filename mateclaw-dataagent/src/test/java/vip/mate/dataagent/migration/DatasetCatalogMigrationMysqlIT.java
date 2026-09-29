package vip.mate.dataagent.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 MySQL 从既有数据集表升级至当前 DataAgent schema。 */
@Testcontainers
class DatasetCatalogMigrationMysqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mateclaw_migration");

    @BeforeEach
    void resetDatabase() throws Exception {
        try (var connection = MYSQL.createConnection("")) {
            var statement = connection.createStatement();
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
            statement.execute("DROP TABLE IF EXISTS dataagent_dashboard_execution");
            statement.execute("DROP TABLE IF EXISTS dataagent_user_uid_mapping");
            statement.execute("DROP TABLE IF EXISTS dataagent_dataset");
        }
    }

    @Test
    void migratesDatasetContractAndAllowsFileDatasetWithoutDatasource() throws Exception {
        try (var connection = MYSQL.createConnection("")) {
            connection.createStatement().execute("""
                    CREATE TABLE dataagent_dataset (
                        id BIGINT NOT NULL PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        datasource_id BIGINT NOT NULL,
                        table_names TEXT,
                        workspace_id BIGINT NOT NULL DEFAULT 1
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            connection.createStatement().execute("INSERT INTO dataagent_dataset (id, name, datasource_id) VALUES (1, 'legacy', 9)");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(true)
                .baselineVersion("217")
                .load();
        flyway.migrate();

        assertEquals("222", flyway.info().current().getVersion().getVersion());
        try (var connection = MYSQL.createConnection("")) {
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "dataagent_dataset", "source_type")) {
                assertNotNull(columns);
                assertEquals(true, columns.next());
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT source_type, schema_version FROM dataagent_dataset WHERE id = 1")) {
                assertEquals(true, rows.next());
                assertEquals("JDBC_TABLE", rows.getString("source_type"));
                assertEquals(1, rows.getInt("schema_version"));
            }
            connection.createStatement().execute("INSERT INTO dataagent_dataset (id, name, datasource_id, source_type) VALUES (2, 'file', NULL, 'FILE')");
        }
    }

    @Test
    void upgradesDatabaseWhereBaseBranchV218AlreadyCreatedUidMapping() throws Exception {
        try (var connection = MYSQL.createConnection("")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE dataagent_dataset (
                        id BIGINT NOT NULL PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        datasource_id BIGINT NOT NULL,
                        table_names TEXT,
                        workspace_id BIGINT NOT NULL DEFAULT 1
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("INSERT INTO dataagent_dataset (id, name, datasource_id) VALUES (1, 'legacy-v218', 9)");
        }

        Flyway legacyFlyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/dataagent-v218-legacy/mysql")
                .baselineOnMigrate(true)
                .baselineVersion("217")
                .load();
        legacyFlyway.migrate();
        try (var connection = MYSQL.createConnection("")) {
            connection.createStatement().execute("""
                    INSERT INTO dataagent_user_uid_mapping (id, username, tenant_id, aloudata_uid)
                    VALUES (1, 'legacy-user', 'legacy-tenant', 'encrypted-uid')
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .load();
        flyway.repair();
        flyway.migrate();

        assertEquals("222", flyway.info().current().getVersion().getVersion());
        try (var connection = MYSQL.createConnection("")) {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT source_type, schema_version FROM dataagent_dataset WHERE id = 1")) {
                assertEquals(true, rows.next());
                assertEquals("JDBC_TABLE", rows.getString("source_type"));
                assertEquals(1, rows.getInt("schema_version"));
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM dataagent_user_uid_mapping WHERE username = 'legacy-user' AND tenant_id = 'legacy-tenant'")) {
                assertEquals(true, rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }
}
