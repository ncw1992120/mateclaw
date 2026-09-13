package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataAnalysisViewQueryCompiler;
import vip.mate.dataagent.service.AloudataAnalysisViewService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AloudataAnalysisViewAdapterTest {
    @Mock DatasetMapper datasetMapper;
    @Mock DatasourceMapper datasourceMapper;
    @Mock AloudataAnalysisViewService viewService;
    @Mock AloudataApiClient apiClient;
    @Mock AloudataConfigHelper configHelper;

    @Test
    void readsViewWithoutFiltersAndReportsRemotePushdown() {
        AloudataAnalysisViewAdapter adapter = adapter();
        DatasetEntity dataset = dataset();
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasetMapper.selectById(7L)).thenReturn(dataset);
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(eq("analysis_view_query_data"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("analysisView", List.of(Map.of("region", "华东", "revenue", 10)))));

        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 5, 0, Map.of()));

        assertEquals(1, batch.rows().size());
        assertTrue(batch.pushdownReport().projectionPushed());
        assertTrue(batch.pushdownReport().limitPushed());
        verify(apiClient).callWithParams(eq("analysis_view_query_data"), any(), argThat(p ->
                Integer.valueOf(5).equals(p.get("pageSize"))));
    }

    @Test
    void compilesDimensionFilterToMetricsQuery() {
        AloudataAnalysisViewAdapter adapter = adapter();
        DatasetEntity dataset = dataset();
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasetMapper.selectById(7L)).thenReturn(dataset);
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(viewService.getByName(3L, "sales")).thenReturn(new AloudataAnalysisViewDetail(
                "v1", "sales", "销售", null,
                List.of(Map.of("name", "revenue")), List.of(Map.of("name", "region")),
                null, List.of(), List.of(), List.of()));
        when(apiClient.callWithParams(eq("metrics_query"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of(Map.of("region", "华东", "revenue", 10)))));

        DatasetFilter filter = new DatasetFilter("region", "dimension", "eq", "华东");
        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(filter), 10, 0, Map.of()));

        assertEquals(1, batch.rows().size());
        assertEquals(List.of(filter), batch.pushdownReport().pushedFilters());
        verify(apiClient).callWithParams(eq("metrics_query"), any(), argThat(p ->
                ((List<?>) p.get("dimensions")).contains("region")));
    }

    @Test
    void rejectsFilterOutsideViewSchemaBeforeRemoteCall() {
        AloudataAnalysisViewAdapter adapter = adapter();
        when(datasetMapper.selectById(7L)).thenReturn(dataset());
        when(datasourceMapper.selectById(3L)).thenReturn(new DatasourceEntity());
        when(viewService.getByName(3L, "sales")).thenReturn(new AloudataAnalysisViewDetail(
                "v1", "sales", "销售", null, List.of(Map.of("name", "revenue")),
                List.of(Map.of("name", "region")), null, List.of(), List.of(), List.of()));

        DatasetFilter filter = new DatasetFilter("not_in_view", "dimension", "eq", "x");
        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(filter), 10, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.UNSUPPORTED_FILTER, error.code());
        verifyNoInteractions(apiClient);
    }

    @Test
    void mapsTimeoutAndEmptyResultWithoutTurningItIntoFailure() {
        AloudataAnalysisViewAdapter adapter = adapter();
        DatasetEntity dataset = dataset();
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasetMapper.selectById(7L)).thenReturn(dataset);
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(eq("analysis_view_query_data"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("analysisView", List.of())));
        DatasetBatch empty = adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 10, 0, Map.of()));
        assertTrue(empty.rows().isEmpty());

        when(apiClient.callWithParams(eq("analysis_view_query_data"), any(), anyMap()))
                .thenThrow(new ResourceAccessException("timeout"));
        DatasetReadException timeout = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 10, 0, Map.of())));
        assertEquals(DatasetReadErrorCode.SOURCE_TIMEOUT, timeout.code());
    }

    @Test
    void mapsRemoteServerErrorToSourceUnavailable() {
        AloudataAnalysisViewAdapter adapter = adapter();
        when(datasetMapper.selectById(7L)).thenReturn(dataset());
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(anyString(), any(), anyMap()))
                .thenThrow(new HttpServerErrorException(org.springframework.http.HttpStatus.BAD_GATEWAY));

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 10, 0, Map.of())));
        assertEquals(DatasetReadErrorCode.SOURCE_UNAVAILABLE, error.code());
    }

    @Test
    void mapsViewAccessDeniedCodeToAccessDenied() {
        AloudataAnalysisViewAdapter adapter = adapter();
        when(datasetMapper.selectById(7L)).thenReturn(dataset());
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(eq("analysis_view_query_data"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "code", "SM_02_0038",
                        "success", false,
                        "message", "view access denied")));

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 5, 0, Map.of())));

        assertEquals(DatasetReadErrorCode.ACCESS_DENIED, error.code());
        assertEquals("VIEW_ACCESS_DENIED", error.getMessage());
    }

    @Test
    void capsRequestedPageSizeAtTenThousand() {
        AloudataAnalysisViewAdapter adapter = adapter();
        when(datasetMapper.selectById(7L)).thenReturn(dataset());
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(eq("analysis_view_query_data"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("analysisView", List.of())));

        adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 1_000_000, 0, Map.of()));

        verify(apiClient).callWithParams(eq("analysis_view_query_data"), any(), argThat(p ->
                Integer.valueOf(10_000).equals(p.get("pageSize"))));
    }

    @Test
    void convertsRowOffsetToZeroBasedPageIndex() {
        AloudataAnalysisViewAdapter adapter = adapter();
        when(datasetMapper.selectById(7L)).thenReturn(dataset());
        DatasourceEntity datasource = new DatasourceEntity();
        when(datasourceMapper.selectById(3L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(eq("analysis_view_query_data"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("analysisView", List.of())));

        adapter.read(new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 5, 10, Map.of()));

        verify(apiClient).callWithParams(eq("analysis_view_query_data"), any(), argThat(p ->
                Integer.valueOf(2).equals(p.get("pageIndex"))
                        && Integer.valueOf(5).equals(p.get("pageSize"))));
    }

    @Test
    void rejectsNonPageAlignedOffsetInsteadOfSilentlyReturningWrongRows() {
        AloudataAnalysisViewAdapter adapter = adapter();
        when(datasetMapper.selectById(7L)).thenReturn(dataset());
        when(datasourceMapper.selectById(3L)).thenReturn(new DatasourceEntity());

        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task-1", Set.of(7L)),
                new DatasetReadRequest(7L, "sales", List.of(), List.of(), 5, 3, Map.of())));

        assertEquals(DatasetReadErrorCode.INVALID_REQUEST, error.code());
        verifyNoInteractions(apiClient);
    }

    private DatasetEntity dataset() {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("sales");
        dataset.setDatasourceId(3L);
        dataset.setSourceType(DatasetSourceType.ALOUDATA_ANALYSIS_VIEW.name());
        dataset.setSourceConfig("{\"viewName\":\"sales\"}");
        return dataset;
    }

    private AloudataAnalysisViewAdapter adapter() {
        return new AloudataAnalysisViewAdapter(datasetMapper, datasourceMapper, viewService,
                new AloudataAnalysisViewQueryCompiler(), configHelper, apiClient, new ObjectMapper());
    }
}
