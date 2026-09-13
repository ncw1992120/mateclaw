package vip.mate.dataagent.dataset.jdbc;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class JdbcDatasetAdapterMysqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("orders");

    @Test
    void pushesFilterAndPagingToMysql() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, status VARCHAR(20))");
            statement.execute("INSERT INTO orders VALUES (1, 'PAID'), (2, 'CANCELLED')");
        }
        DatasetMapper datasets = mock(DatasetMapper.class);
        DatasetFieldMapper fields = mock(DatasetFieldMapper.class);
        DatasourceMapper sources = mock(DatasourceMapper.class);
        DatasetEntity dataset = dataset();
        DatasourceEntity source = source();
        when(datasets.selectById(7L)).thenReturn(dataset);
        when(sources.selectById(3L)).thenReturn(source);
        when(fields.selectList(any())).thenReturn(List.of());
        JdbcDatasetAdapter adapter = new JdbcDatasetAdapter(datasets, fields, sources,
                new JSqlParserValidationService(), new ObjectMapper());

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "orders", List.of(),
                        List.of(new DatasetFilter("status", "dimension", "eq", "PAID")), 1, 0, Map.of()));
        assertEquals(1, batch.rows().size());
        assertEquals("PAID", batch.rows().getFirst().get("status"));
        assertEquals(1, batch.pushdownReport().pushedFilters().size());
    }

    private DatasetEntity dataset() {
        DatasetEntity value = new DatasetEntity();
        value.setId(7L); value.setDatasourceId(3L); value.setName("orders");
        value.setTableNames("orders"); value.setSourceType(DatasetSourceType.JDBC_TABLE.name());
        return value;
    }

    private DatasourceEntity source() {
        DatasourceEntity value = new DatasourceEntity();
        value.setSourceType("mysql"); value.setHost(MYSQL.getHost()); value.setPort(MYSQL.getFirstMappedPort());
        value.setDatabaseName("orders"); value.setUsername(MYSQL.getUsername()); value.setPassword(MYSQL.getPassword());
        return value;
    }
}
