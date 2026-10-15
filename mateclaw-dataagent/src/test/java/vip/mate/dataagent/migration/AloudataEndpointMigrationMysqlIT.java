package vip.mate.dataagent.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AloudataEndpointMigrationMysqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("aloudata_endpoint_migration");

    @Test
    void addsMissingDimensionValuesEndpointAndPreservesExistingConfiguration() throws Exception {
        try (var connection = MYSQL.createConnection("")) {
            connection.createStatement().execute("CREATE TABLE mate_system_setting (id BIGINT PRIMARY KEY, setting_key VARCHAR(128) UNIQUE NOT NULL, setting_value TEXT, description VARCHAR(256), create_time DATETIME NOT NULL, update_time DATETIME NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            connection.createStatement().execute("INSERT INTO mate_system_setting VALUES (1, 'aloudata.api.endpoints', '{\"custom_endpoint\":{\"path\":\"/custom\"}}', 'test', NOW(), NOW())");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(true)
                .baselineVersion("222")
                .load();
        flyway.migrate();

        try (var connection = MYSQL.createConnection("");
             var rows = connection.createStatement().executeQuery("SELECT setting_value FROM mate_system_setting WHERE setting_key = 'aloudata.api.endpoints'")) {
            rows.next();
            JsonNode config = new ObjectMapper().readTree(rows.getString(1));
            assertEquals("/custom", config.path("custom_endpoint").path("path").asText());
            assertEquals("/anymetrics/api/v1/dimension/values", config.path("dimension_values").path("path").asText());
            assertTrue(config.path("dimension_values").path("requestParams").toString().contains("dimValueKeyword"));
        }
    }
}
