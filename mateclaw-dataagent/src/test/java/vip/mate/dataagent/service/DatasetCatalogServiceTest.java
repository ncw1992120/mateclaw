package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;
import vip.mate.dataagent.dataset.DatasetSourceType;
import vip.mate.dataagent.dto.DatasetCreateRequest;
import vip.mate.dataagent.dto.DatasetSourceDefinition;
import vip.mate.dataagent.dto.DatasetUpdateRequest;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetDataMapper;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceColumnMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.repository.DatasourceTableMapper;
import vip.mate.dataagent.service.impl.DatasetManageServiceImpl;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetCatalogServiceTest {

    @Mock DatasetMapper datasetMapper;
    @Mock DatasetFieldMapper datasetFieldMapper;
    @Mock DatasetDataMapper datasetDataMapper;
    @Mock DatasourceMapper datasourceMapper;
    @Mock DatasourceTableMapper datasourceTableMapper;
    @Mock DatasourceColumnMapper datasourceColumnMapper;
    @Mock DatasourceManageService datasourceManageService;
    @Mock WorkspaceGuard workspaceGuard;

    @Test
    void descriptorDefaultsLegacyDatasetToJdbcTableAndDoesNotExposeConfig() throws Exception {
        DatasetManageServiceImpl service = newService();
        DatasetEntity entity = new DatasetEntity();
        entity.setId(7L);
        entity.setName("orders");
        entity.setWorkspaceId(11L);
        entity.setRowCount(12L);
        entity.setDatasourceId(3L);
        entity.setTableNames("orders");
        when(datasetMapper.selectById(7L)).thenReturn(entity);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        when(datasetFieldMapper.selectList(any())).thenReturn(List.of());

        DatasetInputDescriptor descriptor = service.getInputDescriptor(
                new DatasetAccessContext(11L, 99L, "task-1", Set.of(7L)), 7L, "orders");

        assertEquals(DatasetSourceType.JDBC_TABLE, descriptor.sourceType());
        assertEquals(12L, descriptor.rowCount());
        assertFalse(descriptor.sourceConfig().containsKey("password"));
        assertFalse(new ObjectMapper().writeValueAsString(descriptor).contains("sourceConfig"));
    }

    @Test
    void descriptorRejectsDatasetOutsideTaskAllowList() {
        DatasetManageServiceImpl service = newService();

        assertThrows(RuntimeException.class, () -> service.getInputDescriptor(
                new DatasetAccessContext(11L, 99L, "task-1", Set.of()), 7L, "orders"));
        verifyNoInteractions(datasetMapper);
    }

    @Test
    void createsTypedJdbcSqlDatasetAndPersistsOnlyDefinition() throws Exception {
        DatasetManageServiceImpl service = newService();
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(3L);
        datasource.setName("warehouse");
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        doAnswer(invocation -> {
            DatasetEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return 1;
        }).when(datasetMapper).insert((DatasetEntity) any(DatasetEntity.class));

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("sales sql");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));

        service.createDataset(request);

        verify(datasourceManageService).checkDatasourceReadable(3L);
        verify(datasetMapper).insert((DatasetEntity) argThat((DatasetEntity entity) ->
                "JDBC_SQL".equals(entity.getSourceType())
                        && entity.getDatasourceId().equals(3L)
                && entity.getSourceConfig().contains("select id from orders")));
    }

    @Test
    void normalizesDatasetNameWithNfkcAndRejectsBlankName() {
        DatasetManageServiceImpl service = newService();
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(3L);
        datasource.setName("warehouse");
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        doAnswer(invocation -> {
            ((DatasetEntity) invocation.getArgument(0)).setId(9L);
            return 1;
        }).when(datasetMapper).insert((DatasetEntity) any(DatasetEntity.class));

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("  Ｓａｌｅｓ　１  ");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));

        service.createDataset(request);

        verify(datasetMapper).insert((DatasetEntity) argThat((DatasetEntity entity) -> "Sales 1".equals(entity.getName())));
        DatasetCreateRequest blank = new DatasetCreateRequest();
        blank.setName("  ");
        blank.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));
        assertThrows(IllegalArgumentException.class, () -> service.createDataset(blank));
    }

    @Test
    void rejectsDuplicateDatasetNameWithinWorkspace() {
        DatasetManageServiceImpl service = newService();
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(datasetMapper.selectCount(any())).thenReturn(1L);

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("Sales");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));

        assertThrows(IllegalArgumentException.class, () -> service.createDataset(request));
        verifyNoInteractions(datasourceMapper);
    }

    @Test
    void rejectsTypedDatasetWhenDatasourceIsNotReadable() {
        DatasetManageServiceImpl service = newService();
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(3L);
        datasource.setName("private warehouse");
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        doThrow(new RuntimeException("无权访问该数据源"))
                .when(datasourceManageService).checkDatasourceReadable(3L);

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("private sales");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));

        assertThrows(RuntimeException.class, () -> service.createDataset(request));
        verify(datasetMapper, never()).insert(any(DatasetEntity.class));
    }

    @Test
    void rejectsJdbcDefinitionBoundToAloudataDatasource() {
        DatasetManageServiceImpl service = newService();
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(60L);
        datasource.setName("metrics");
        datasource.setSourceType("aloudata");
        when(datasourceMapper.selectById(60L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("invalid jdbc");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(60L, "select 1"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDataset(request));
        assertEquals("JDBC 数据集不能绑定 Aloudata 数据源", error.getMessage());
        verify(datasetMapper, never()).insert(any(DatasetEntity.class));
    }

    @Test
    void rejectsAloudataViewBoundToJdbcDatasource() {
        DatasetManageServiceImpl service = newService();
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(3L);
        datasource.setName("warehouse");
        datasource.setSourceType("mysql");
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("invalid view");
        request.setSourceDefinition(new DatasetSourceDefinition.AloudataViewDefinition(3L, "sales_view"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDataset(request));
        assertEquals("Aloudata 指标视图必须绑定 Aloudata 数据源", error.getMessage());
        verify(datasetMapper, never()).insert(any(DatasetEntity.class));
    }

    @Test
    void updatesTypedSourceDefinitionWithoutChangingLegacyBasicFields() throws Exception {
        DatasetManageServiceImpl service = newService();
        DatasetEntity entity = new DatasetEntity();
        entity.setId(8L);
        entity.setName("old");
        entity.setWorkspaceId(11L);
        entity.setOwnerId(99L);
        entity.setSourceType("JDBC_TABLE");
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(3L);
        datasource.setName("warehouse");
        when(datasetMapper.selectById(8L)).thenReturn(entity);
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);

        DatasetUpdateRequest request = new DatasetUpdateRequest();
        request.setName("new");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));

        service.updateDataset(8L, request);

        verify(datasourceManageService).checkDatasourceReadable(3L);
        verify(datasetMapper).updateById(argThat((DatasetEntity updated) ->
                "new".equals(updated.getName())
                        && "JDBC_SQL".equals(updated.getSourceType())
                        && updated.getDatasourceId().equals(3L)
                && updated.getSourceConfig().contains("select id from orders")));
    }

    @Test
    void resolvesRegisteredHttpApiDefinitionIntoDatasetSourceConfig() throws Exception {
        DatasetManageServiceImpl service = newService();
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(4L);
        datasource.setName("orders api");
        datasource.setConnectionParams(new ObjectMapper().writeValueAsString(Map.of(
                "apiDefinitions", Map.of("orders", Map.of(
                        "endpoint", "http://e2e-http:8080/orders",
                        "method", "GET",
                        "allowedHosts", List.of("e2e-http"),
                        "allowedQueryParams", List.of("status"),
                        "paginationMode", "none",
                        "resultPath", "$.data")))));
        when(datasourceMapper.selectById(4L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        doAnswer(invocation -> {
            ((DatasetEntity) invocation.getArgument(0)).setId(12L);
            return 1;
        }).when(datasetMapper).insert((DatasetEntity) any(DatasetEntity.class));

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("registered orders");
        request.setSourceDefinition(new DatasetSourceDefinition.HttpApiDefinition(4L, "orders"));

        service.createDataset(request);

        verify(datasetMapper).insert((DatasetEntity) argThat((DatasetEntity entity) ->
                "HTTP_API".equals(entity.getSourceType())
                        && entity.getSourceConfig().contains("http://e2e-http:8080/orders")
                && entity.getSourceConfig().contains("apiDefinitionId")));
    }

    @Test
    void rejectsUnregisteredHttpApiDefinitionBeforePersisting() throws Exception {
        DatasetManageServiceImpl service = newService();
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(4L);
        datasource.setName("orders api");
        datasource.setConnectionParams(new ObjectMapper().writeValueAsString(Map.of(
                "apiDefinitions", Map.of("orders", Map.of(
                        "endpoint", "https://api.example/orders",
                        "method", "GET",
                        "allowedHosts", List.of("api.example"),
                        "allowedQueryParams", List.of("status"),
                        "paginationMode", "none",
                        "resultPath", "$.data")))));
        when(datasourceMapper.selectById(4L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("unregistered orders");
        request.setSourceDefinition(new DatasetSourceDefinition.HttpApiDefinition(4L, "missing"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.createDataset(request));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals("HTTP API 登记定义不存在: missing", error.getCause().getMessage());
        verify(datasetMapper, never()).insert(any(DatasetEntity.class));
    }

    private DatasetManageServiceImpl newService() {
        return new DatasetManageServiceImpl(datasetMapper, datasetFieldMapper, datasetDataMapper,
                datasourceMapper, datasourceTableMapper, datasourceColumnMapper,
                new ObjectMapper(), workspaceGuard, datasourceManageService);
    }
}
