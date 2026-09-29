package vip.mate.dataagent.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AloudataEndpointMigrationPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.6")
            .withDatabaseName("aloudata_endpoint_migration");

    @Test
    void addsMissingDimensionValuesEndpointAndPreservesExistingConfiguration() throws Exception {
        try (var connection = POSTGRES.createConnection("")) {
            connection.createStatement().execute("CREATE TABLE mate_system_setting (id BIGINT PRIMARY KEY, setting_key VARCHAR(128) UNIQUE NOT NULL, setting_value TEXT, description VARCHAR(256), create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL)");
            connection.createStatement().execute("INSERT INTO mate_system_setting VALUES (1, 'aloudata.api.endpoints', '{\"custom_endpoint\":{\"path\":\"/custom\"}}', 'test', NOW(), NOW())");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .baselineOnMigrate(true)
                .baselineVersion("222")
                .load();
        flyway.migrate();

        try (var connection = POSTGRES.createConnection("");
             var rows = connection.createStatement().executeQuery("SELECT setting_value FROM mate_system_setting WHERE setting_key = 'aloudata.api.endpoints'")) {
            rows.next();
            JsonNode config = new ObjectMapper().readTree(rows.getString(1));
            assertEquals("/custom", config.path("custom_endpoint").path("path").asText());
            assertEquals("/anymetrics/api/v1/dimension/values", config.path("dimension_values").path("path").asText());
            assertTrue(config.path("dimension_values").path("requestParams").toString().contains("dimValueKeyword"));
        }
    }
}
