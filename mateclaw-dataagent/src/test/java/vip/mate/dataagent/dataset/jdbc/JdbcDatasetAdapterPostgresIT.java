package vip.mate.dataagent.dataset.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceMapper;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
class JdbcDatasetAdapterPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.6").withDatabaseName("orders");

    @Test
    void pushesFilterAndPagingToPostgres() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, status VARCHAR(20))");
            statement.execute("INSERT INTO orders VALUES (1, 'PAID'), (2, 'CANCELLED')");
        }
        DatasetMapper datasets = mock(DatasetMapper.class);
        DatasetFieldMapper fields = mock(DatasetFieldMapper.class);
        DatasourceMapper sources = mock(DatasourceMapper.class);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L); dataset.setDatasourceId(3L); dataset.setName("orders");
        dataset.setTableNames("orders"); dataset.setSourceType(DatasetSourceType.JDBC_TABLE.name());
        DatasourceEntity source = new DatasourceEntity();
        source.setSourceType("postgresql"); source.setHost(POSTGRES.getHost()); source.setPort(POSTGRES.getFirstMappedPort());
        source.setDatabaseName("orders"); source.setUsername(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        when(datasets.selectById(7L)).thenReturn(dataset); when(sources.selectById(3L)).thenReturn(source);
        when(fields.selectList(any())).thenReturn(List.of());
        JdbcDatasetAdapter adapter = new JdbcDatasetAdapter(datasets, fields, sources,
                new JSqlParserValidationService(), new ObjectMapper());

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(),
                        List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 1, 0, Map.of()));
        assertEquals(1, batch.rows().size());
        assertEquals("PAID", batch.rows().getFirst().get("status"));
    }
}
