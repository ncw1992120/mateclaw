package vip.mate.dataagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.jdbc.SqlValidationService;
import vip.mate.dataagent.dataset.http.HttpApiRequestPolicy;
import vip.mate.dataagent.dataset.http.HttpApiDatasetAdapter;
import vip.mate.dataagent.dataset.file.FileDatasetAdapter;
import vip.mate.dataagent.dataset.AloudataAnalysisViewAdapter;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetComposerControllerTest {
    @Test
    void aloudataMetricsDraftPreviewConvertsColumnarResponseToRows() {
        AloudataService aloudata = mock(AloudataService.class);
        AloudataMetricQueryResponse response = new AloudataMetricQueryResponse();
        response.setCode("200");
        AloudataMetricQueryResponse.MetricData data = new AloudataMetricQueryResponse.MetricData();
        data.setColumns(Map.of(
                "metric_time", List.of(column("2026-09-01")),
                "digo_touch_cnt_1", List.of(column(984))));
        data.setTotal(1L);
        response.setData(data);
        when(aloudata.queryMetrics(eq(3L), any())).thenReturn(response);

        DatasetComposerController controller = new DatasetComposerController(
                mock(DatasetManageService.class), mock(SqlValidationService.class), new ObjectMapper(),
                mock(WorkspaceGuard.class), mock(DatasourceMapper.class), new HttpApiRequestPolicy(true),
                aloudata, mock(HttpApiDatasetAdapter.class), mock(FileDatasetAdapter.class),
                mock(AloudataAnalysisViewAdapter.class));
        DatasetComposerController.DraftRequest request = new DatasetComposerController.DraftRequest();
        request.setSourceType("ALOUDATA_METRICS");
        request.setDatasourceId("3");
        request.setSourceConfig(Map.of("dimensions", List.of("metric_time"), "metrics", List.of("digo_touch_cnt_1")));
        request.setColumns(List.of("digo_touch_cnt_1"));
        request.setLimit(50);

        Map<String, Object> preview = controller.preview(request).getData();

        assertEquals(1, preview.get("rowCount"));
        assertEquals(List.of(Map.of("metric_time", "2026-09-01", "digo_touch_cnt_1", 984)), preview.get("rows"));
        List<?> schema = (List<?>) preview.get("schema");
        assertEquals(2, schema.size());
        assertTrue(schema.contains("metric_time"));
        assertTrue(schema.contains("digo_touch_cnt_1"));
    }

    @Test
    void rejectsFilePreviewWithoutObjectReferenceBeforeAdapterCall() {
        FileDatasetAdapter fileAdapter = mock(FileDatasetAdapter.class);
        DatasetComposerController controller = new DatasetComposerController(
                mock(DatasetManageService.class), mock(SqlValidationService.class), new ObjectMapper(),
                mock(WorkspaceGuard.class), mock(DatasourceMapper.class), new HttpApiRequestPolicy(true),
                mock(AloudataService.class), mock(HttpApiDatasetAdapter.class), fileAdapter,
                mock(AloudataAnalysisViewAdapter.class));
        DatasetComposerController.DraftRequest request = new DatasetComposerController.DraftRequest();
        request.setSourceType("FILE");
        request.setSourceConfig(Map.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> controller.preview(request));
        assertEquals("file objectId is required", error.getMessage());
        verifyNoInteractions(fileAdapter);
    }

    @Test
    void registersControlledApiDefinitionAndPersistsOnlyDeclaration() throws Exception {
        DatasourceMapper mapper = mock(DatasourceMapper.class); WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DatasourceEntity source = source(); source.setConnectionParams("{\"allowedHosts\":[\"e2e-http\"]}");
        when(mapper.selectById(7L)).thenReturn(source); when(guard.currentWorkspaceId()).thenReturn(11L); when(guard.currentUserId()).thenReturn(22L);
        DatasetComposerController controller = controller(mapper, guard);
        DatasetComposerController.ApiDefinitionRequest request = new DatasetComposerController.ApiDefinitionRequest();
        request.setDatasourceId("7"); request.setEndpoint("http://e2e-http:8080/orders"); request.setMethod("GET"); request.setAllowedQueryParams(java.util.List.of("status"));
        var result = controller.registerApiDefinition(request);
        assertNotNull(result.getData().get("apiDefinitionId"));
        assertTrue(source.getConnectionParams().contains("apiDefinitions"));
        assertTrue(source.getConnectionParams().contains("allowedQueryParams"));
        verify(mapper).updateById(source);
    }

    @Test
    void rejectsSensitiveHeadersBeforePersistence() {
        DatasourceMapper mapper = mock(DatasourceMapper.class); WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DatasourceEntity source = source(); source.setConnectionParams("{\"allowedHosts\":[\"e2e-http\"]}");
        when(mapper.selectById(7L)).thenReturn(source); when(guard.currentWorkspaceId()).thenReturn(11L); when(guard.currentUserId()).thenReturn(22L);
        DatasetComposerController.ApiDefinitionRequest request = new DatasetComposerController.ApiDefinitionRequest();
        request.setDatasourceId("7"); request.setEndpoint("http://e2e-http:8080/orders"); request.setHeaders(Map.of("Authorization", "secret"));
        assertThrows(IllegalArgumentException.class, () -> controller(mapper, guard).registerApiDefinition(request));
        assertEquals("{\"allowedHosts\":[\"e2e-http\"]}", source.getConnectionParams());
    }

    private DatasetComposerController controller(DatasourceMapper mapper, WorkspaceGuard guard) {
        return new DatasetComposerController(mock(DatasetManageService.class), mock(SqlValidationService.class), new ObjectMapper(), guard,
                mapper, new HttpApiRequestPolicy(true), mock(AloudataService.class), mock(HttpApiDatasetAdapter.class), mock(FileDatasetAdapter.class), mock(AloudataAnalysisViewAdapter.class));
    }
    private DatasourceEntity source() { DatasourceEntity source = new DatasourceEntity(); source.setId(7L); source.setWorkspaceId(11L); source.setOwnerId(22L); source.setMetaShared(true); source.setHost("e2e-http"); return source; }

    private AloudataMetricQueryResponse.ColumnValue column(Object value) {
        AloudataMetricQueryResponse.ColumnValue column = new AloudataMetricQueryResponse.ColumnValue();
        column.setValue(value);
        return column;
    }
}
