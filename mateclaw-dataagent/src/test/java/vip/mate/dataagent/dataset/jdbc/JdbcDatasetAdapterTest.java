package vip.mate.dataagent.dataset.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcDatasetAdapterTest {
    @Test
    void supportsOnlyJdbcDatasetKindsAndEnforcesTaskAllowList() {
        DatasetMapper datasets = mock(DatasetMapper.class);
        DatasetFieldMapper fields = mock(DatasetFieldMapper.class);
        JdbcDatasetAdapter adapter = new JdbcDatasetAdapter(datasets, fields, mock(DatasourceMapper.class),
                new JSqlParserValidationService(), new ObjectMapper());

        assertTrue(adapter.supports(DatasetSourceType.JDBC_TABLE));
        assertTrue(adapter.supports(DatasetSourceType.JDBC_SQL));
        assertFalse(adapter.supports(DatasetSourceType.FILE));
        assertThrows(DatasetReadException.class, () -> adapter.describe(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of()), 8L));
        verifyNoInteractions(datasets);
    }

    @Test
    void descriptorUsesPersistedSchemaWithoutConnectionDetails() {
        DatasetMapper datasets = mock(DatasetMapper.class);
        DatasetFieldMapper fields = mock(DatasetFieldMapper.class);
        DatasetEntity entity = new DatasetEntity();
        entity.setId(8L);
        entity.setName("orders");
        entity.setDatasourceId(3L);
        entity.setSourceType(DatasetSourceType.JDBC_SQL.name());
        entity.setRowCount(42L);
        when(datasets.selectById(8L)).thenReturn(entity);
        when(fields.selectList(any())).thenReturn(List.of());
        JdbcDatasetAdapter adapter = new JdbcDatasetAdapter(datasets, fields, mock(DatasourceMapper.class),
                new JSqlParserValidationService(), new ObjectMapper());

        DatasetInputDescriptor descriptor = adapter.describe(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(8L)), 8L);
        assertEquals(DatasetSourceType.JDBC_SQL, descriptor.sourceType());
        assertEquals(42L, descriptor.rowCount());
        assertTrue(descriptor.sourceConfig().containsKey("datasourceId"));
    }
}
