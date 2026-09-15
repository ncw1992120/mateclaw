package vip.mate.dataagent.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InsightDashboardSchemaDTOTest {

    @Test
    void dataSourceCarriesJdbcQueryBindingFields() {
        InsightDashboardSchemaDTO.DataSource dataSource = new InsightDashboardSchemaDTO.DataSource();
        dataSource.setDatasourceId("42");
        dataSource.setSourceType("JDBC");
        dataSource.setSql("select status, count(*) as total from orders group by status");

        assertEquals("JDBC", dataSource.getSourceType());
        assertEquals("select status, count(*) as total from orders group by status", dataSource.getSql());
    }

    @Test
    void schemaCarriesDatasetComposerFieldsWhenDashboardIsRewritten() {
        InsightDashboardSchemaDTO schema = new InsightDashboardSchemaDTO();
        schema.setDatasetInputs(new java.util.ArrayList<>());
        schema.setScriptFilterBindings(new java.util.ArrayList<>());
        schema.setScript("result = []");

        assertNotNull(schema.getDatasetInputs());
        assertNotNull(schema.getScriptFilterBindings());
        assertEquals("result = []", schema.getScript());
    }
}
