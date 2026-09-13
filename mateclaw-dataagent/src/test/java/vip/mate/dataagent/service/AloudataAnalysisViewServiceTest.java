package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.impl.AloudataAnalysisViewServiceImpl;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AloudataAnalysisViewServiceTest {
    @Mock DatasourceMapper datasourceMapper;
    @Mock AloudataConfigHelper configHelper;
    @Mock AloudataApiClient apiClient;
    @Mock DatasourceManageService datasourceManageService;

    @Test
    void mapsTreeAndDetailWithoutExposingAuthHeaders() {
        AloudataAnalysisViewServiceImpl service = service();
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(9L);
        ds.setSourceType("aloudata");
        when(datasourceMapper.selectById(9L)).thenReturn(ds);
        when(datasourceManageService.getDatasource(9L)).thenReturn(null);
        when(datasourceManageService.getDatasource(9L)).thenReturn(null);
        when(configHelper.parseConfig(ds)).thenReturn(new AloudataConfigDTO());

        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("categoryId", "cat-1");
        tree.put("categoryName", "销售");
        tree.put("analysisViewList", List.of(Map.of("id", "v1", "viewName", "sales_view", "displayName", "销售视图")));
        when(apiClient.callWithParams(eq("analysis_view_tree"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(tree));

        var summaries = service.listTree(9L);
        assertEquals(1, summaries.size());
        assertEquals("sales_view", summaries.getFirst().viewName());
        assertEquals("cat-1", summaries.getFirst().categoryId());
        verify(apiClient).callWithParams(eq("analysis_view_tree"), any(), eq(Map.of()));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("data", Map.of("id", "v1", "viewName", "sales_view", "displayName", "销售视图",
                "metrics", List.of(Map.of("name", "revenue")),
                "dimensions", List.of(Map.of("name", "region")),
                "timeConstraint", "(metric_time >= '2024-01-01')",
                "filters", List.of(Map.of("field", "region", "operator", "eq", "value", "华东")),
                "resultFilters", List.of()));
        when(apiClient.callWithParams(eq("analysis_view_query_by_name"), any(), eq(Map.of("viewName", "sales_view"))))
                .thenReturn(ResponseEntity.ok(detail));

        var view = service.getByName(9L, "sales_view");
        assertEquals("sales_view", view.viewName());
        assertEquals(1, view.metrics().size());
        assertEquals("(metric_time >= '2024-01-01')", view.timeConstraint());
    }

    @Test
    void mapsAccessDeniedCodeToViewAccessDenied() {
        AloudataAnalysisViewServiceImpl service = service();
        DatasourceEntity ds = new DatasourceEntity();
        ds.setSourceType("aloudata");
        when(datasourceMapper.selectById(9L)).thenReturn(ds);
        when(configHelper.parseConfig(ds)).thenReturn(new AloudataConfigDTO());
        when(apiClient.callWithParams(anyString(), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("code", "SM_02_0038", "message", "denied")));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.listTree(9L));
        assertTrue(error.getMessage().contains("VIEW_ACCESS_DENIED"));
    }

    private AloudataAnalysisViewServiceImpl service() {
        return new AloudataAnalysisViewServiceImpl(datasourceMapper, datasourceManageService,
                configHelper, apiClient, new ObjectMapper());
    }
}
