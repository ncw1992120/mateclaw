package vip.mate.dataagent.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证 PostgreSQL 从既有 V217 数据集表连续迁移至当前 V220。 */
@Testcontainers
class DatasetCatalogMigrationPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.6")
            .withDatabaseName("mateclaw_migration");

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

        assertEquals("220", flyway.info().current().getVersion().getVersion());
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
}
