package vip.mate.dataagent.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetSourceDefinitionTest {

    @Test
    void jdbcSqlDefinitionCarriesSqlOnlyForJdbcSqlSource() {
        DatasetSourceDefinition definition = new DatasetSourceDefinition.JdbcSqlDefinition(7L, "select id from orders");

        assertEquals("JDBC_SQL", definition.sourceType());
        assertEquals(7L, ((DatasetSourceDefinition.JdbcSqlDefinition) definition).datasourceId());
        assertEquals("select id from orders", ((DatasetSourceDefinition.JdbcSqlDefinition) definition).sql());
    }

    @Test
    void definitionsRejectBlankRequiredValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetSourceDefinition.JdbcSqlDefinition(7L, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetSourceDefinition.AloudataViewDefinition(7L, ""));
    }

    @Test
    void oldRequestShapeRemainsAvailableAlongsideTypedDefinition() {
        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setDatasourceId("7");
        request.setTableIds(List.of("11"));

        assertEquals("7", request.getDatasourceId());
        assertEquals(List.of("11"), request.getTableIds());
    }

    @Test
    void aloudataMetricsSourceAllowsDimensionOnlyDatasets() {
        assertDoesNotThrow(() -> new DatasetSourceDefinition.AloudataMetricsDefinition(
                1L, List.of(), List.of("attribution_plan_id", "channel")));
    }

    @Test
    void aloudataMetricsSourceRequiresAtLeastOneMetricOrDimension() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatasetSourceDefinition.AloudataMetricsDefinition(1L, List.of(), List.of()));
    }
}
