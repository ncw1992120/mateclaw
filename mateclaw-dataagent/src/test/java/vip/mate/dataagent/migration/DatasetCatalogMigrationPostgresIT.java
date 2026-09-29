package vip.mate.dataagent.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 PostgreSQL 从既有数据集表升级至当前 DataAgent schema。 */
@Testcontainers
class DatasetCatalogMigrationPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.6")
            .withDatabaseName("mateclaw_migration");

    @BeforeEach
    void resetDatabase() throws Exception {
        try (var connection = POSTGRES.createConnection("")) {
            var statement = connection.createStatement();
            statement.execute("DROP TABLE IF EXISTS flyway_schema_history");
            statement.execute("DROP TABLE IF EXISTS dataagent_dashboard_execution");
            statement.execute("DROP TABLE IF EXISTS dataagent_user_uid_mapping");
            statement.execute("DROP TABLE IF EXISTS dataagent_dataset");
            statement.execute("DROP TABLE IF EXISTS mate_system_setting");
            statement.execute("CREATE TABLE mate_system_setting (id BIGINT PRIMARY KEY, setting_key VARCHAR(128) UNIQUE NOT NULL, setting_value TEXT, description VARCHAR(256), create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL)");
            statement.execute("DROP FUNCTION IF EXISTS set_update_time() CASCADE");
            statement.execute("""
                    CREATE FUNCTION set_update_time() RETURNS trigger AS $$
                    BEGIN
                        NEW.update_time = CURRENT_TIMESTAMP;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
        }
    }

    @Test
    void migratesDatasetContractAndAllowsFileDatasetWithoutDatasource() throws Exception {
        try (var connection = POSTGRES.createConnection("")) {
            connection.createStatement().execute("""
                    CREATE TABLE dataagent_dataset (
                        id BIGINT NOT NULL PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        datasource_id BIGINT NOT NULL,
                        table_names TEXT,
                        workspace_id BIGINT NOT NULL DEFAULT 1
                    )
                    """);
            connection.createStatement().execute("INSERT INTO dataagent_dataset (id, name, datasource_id) VALUES (1, 'legacy', 9)");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .baselineOnMigrate(true)
                .baselineVersion("217")
                .load();
        flyway.migrate();

        assertEquals("223", flyway.info().current().getVersion().getVersion());
        try (var connection = POSTGRES.createConnection("")) {
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
        try (var connection = POSTGRES.createConnection("")) {
            var statement = connection.createStatement();
            statement.execute("""
                    CREATE TABLE dataagent_dataset (
                        id BIGINT NOT NULL PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        datasource_id BIGINT NOT NULL,
                        table_names TEXT,
                        workspace_id BIGINT NOT NULL DEFAULT 1
                    )
                    """);
            statement.execute("INSERT INTO dataagent_dataset (id, name, datasource_id) VALUES (1, 'legacy-v218', 9)");
        }

        Flyway legacyFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/dataagent-v218-legacy/postgresql")
                .baselineOnMigrate(true)
                .baselineVersion("217")
                .load();
        legacyFlyway.migrate();
        try (var connection = POSTGRES.createConnection("")) {
            connection.createStatement().execute("""
                    INSERT INTO dataagent_user_uid_mapping (id, username, tenant_id, aloudata_uid)
                    VALUES (1, 'legacy-user', 'legacy-tenant', 'encrypted-uid')
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load();
        flyway.repair();
        flyway.migrate();

        assertEquals("223", flyway.info().current().getVersion().getVersion());
        try (var connection = POSTGRES.createConnection("")) {
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
