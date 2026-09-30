package vip.mate.dataagent.aloudata;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.system.service.SystemSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AloudataEndpointServiceTest {

    @Test
    void malformedDatabaseConfigurationFallsBackToCoreDatasetEndpoints() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("{malformed");

        AloudataEndpointService service = new AloudataEndpointService(
                new AloudataApiProperties(settings, new ObjectMapper()));

        AloudataApiProperties.ApiEndpoint detail = service.getEndpoint("analysis_view_query_by_name");
        AloudataApiProperties.ApiEndpoint metrics = service.getEndpoint("metrics_query");

        assertNotNull(detail);
        assertEquals("/anymetrics/api/v1/analysisview/queryByName", detail.getPath());
        assertNotNull(metrics);
        assertEquals("/semantic/api/v1.1/metrics/query", metrics.getPath());
        assertTrue(metrics.getRequestParams().stream().anyMatch(p -> "filters".equals(p.getName())));
    }

    @Test
    void incompleteDatabaseConfigurationKeepsCoreSyncEndpointFallbacks() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn(
                "{\"metrics_query\":{\"service\":\"semantic\",\"path\":\"/custom/metrics\",\"method\":\"POST\"}}"
        );

        AloudataEndpointService service = new AloudataEndpointService(
                new AloudataApiProperties(settings, new ObjectMapper()));

        assertEquals("/custom/metrics", service.getEndpoint("metrics_query").getPath());
        assertEquals("/anymetrics/api/v1/metrics/list", service.getEndpoint("metric_list").getPath());
        assertEquals("/anymetrics/api/v1/category/list", service.getEndpoint("category_list").getPath());
        assertEquals("/anymetrics/api/v1/metrics/treeList", service.getEndpoint("metric_tree").getPath());
    }

    @Test
    void forwardsLiveDirectoryKeywordsAndCategoryIdsUsingDefaultEndpointContracts() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("");
        AloudataEndpointService service = new AloudataEndpointService(
                new AloudataApiProperties(settings, new ObjectMapper()));
        AloudataConfigDTO config = new AloudataConfigDTO();

        Map<String, Object> metricParams = service.buildParamsFromConfigAndInput("metric_list", config,
                Map.of("keyword", "销售金额", "metricCategoryId", "metric-category-1"));
        Map<String, Object> dimensionParams = service.buildParamsFromConfigAndInput("dimension_list", config,
                Map.of("keyword", "所属大区", "categoryId", "dimension-category-2"));

        assertEquals("销售金额", metricParams.get("keyword"));
        assertEquals("metric-category-1", metricParams.get("metricCategoryId"));
        assertEquals("所属大区", dimensionParams.get("keyword"));
        assertEquals("dimension-category-2", dimensionParams.get("categoryId"));
    }

    @Test
    void providesDimensionValuesEndpointContractForFilterOptions() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("");
        AloudataEndpointService service = new AloudataEndpointService(
                new AloudataApiProperties(settings, new ObjectMapper()));
        AloudataConfigDTO config = new AloudataConfigDTO();

        AloudataApiProperties.ApiEndpoint endpoint = service.getEndpoint("dimension_values");
        Map<String, Object> params = service.buildParamsFromConfigAndInput("dimension_values", config,
                Map.of("dimName", "shop_code_agent", "dimValueKeyword", "店", "pageNumber", 1, "pageSize", 200));

        assertNotNull(endpoint);
        assertEquals("/anymetrics/api/v1/dimension/values", endpoint.getPath());
        assertEquals("POST", endpoint.getMethod());
        assertEquals("shop_code_agent", params.get("dimName"));
        assertEquals("店", params.get("dimValueKeyword"));
        assertEquals(1, params.get("pageNumber"));
        assertEquals(200, params.get("pageSize"));
        assertTrue(endpoint.getRequestParams().stream().anyMatch(param -> "dimValueKeyword".equals(param.getName())));
        assertFalse(endpoint.getRequestParams().stream().anyMatch(param -> "keyword".equals(param.getName())));
    }

    @Test
    void providesMetricDimensionEndpointContractForLiveCompatibilityChecks() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getString("aloudata.api.endpoints", "")).thenReturn("");
        AloudataEndpointService service = new AloudataEndpointService(
                new AloudataApiProperties(settings, new ObjectMapper()));

        AloudataApiProperties.ApiEndpoint endpoint = service.getEndpoint("metric_dimensions");
        Map<String, Object> params = service.buildParamsFromConfigAndInput("metric_dimensions", new AloudataConfigDTO(),
                Map.of("metricNames", java.util.List.of("metric_a")));

        assertNotNull(endpoint);
        assertEquals("/anymetrics/api/v1/metrics/dimension", endpoint.getPath());
        assertEquals("GET", endpoint.getMethod());
        assertEquals(java.util.List.of("metric_a"), params.get("metricNames"));
    }
}
