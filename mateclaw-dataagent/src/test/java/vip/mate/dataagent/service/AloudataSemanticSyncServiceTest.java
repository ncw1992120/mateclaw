package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.dto.AloudataDimensionPageQuery;
import vip.mate.dataagent.dto.AloudataMetricPageQuery;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.repository.AloudataCategoryMapper;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.service.impl.AloudataSemanticSyncServiceImpl;
import vip.mate.llm.service.ModelConfigService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;

class AloudataSemanticSyncServiceTest {

    @Test
    void enrichesLiveMetricPageWithDimensionCodesFromDimensionAll() {
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AloudataConfigHelper configHelper = mock(AloudataConfigHelper.class);
        AloudataApiClient apiClient = mock(AloudataApiClient.class);
        AloudataEndpointService endpointService = mock(AloudataEndpointService.class);
        AloudataSemanticSyncServiceImpl service = new AloudataSemanticSyncServiceImpl(
                mock(AloudataMetricMapper.class),
                mock(AloudataDimensionMapper.class),
                mock(AloudataMetricDimensionMapper.class),
                mock(AloudataCategoryMapper.class),
                datasourceMapper,
                apiClient,
                configHelper,
                endpointService,
                mock(AloudataSemanticEsService.class),
                mock(ModelConfigService.class),
                mock(AloudataService.class));
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setSourceType("aloudata");
        AloudataConfigDTO config = new AloudataConfigDTO();
        when(datasourceMapper.selectById(9L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(config);
        when(endpointService.buildParamsFromConfigAndInput(eq("metric_list"), eq(config), anyMap()))
                .thenReturn(new java.util.HashMap<>());
        when(endpointService.buildParamsFromConfigAndInput(eq("metric_all_dimensions"), eq(config), anyMap()))
                .thenReturn(new java.util.HashMap<>());
        when(apiClient.callWithParams(eq("metric_list"), eq(config), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "data", Map.of("total", 1,
                        "data", List.of(Map.of("metricName", "metric_a", "metricDisplayName", "指标 A"))))));
        when(apiClient.callWithParams(eq("metric_all_dimensions"), eq(config), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "data", Map.of("metric_a",
                        List.of(Map.of("dimName", "region", "dimDisplayName", "所属大区"))))));
        AloudataMetricPageQuery query = new AloudataMetricPageQuery();
        query.setPageNumber(1);
        query.setPageSize(20);

        var result = service.pageMetrics(9L, query);

        assertEquals(List.of("region"), result.getRecords().get(0).getAvailableDimensions());
    }

    @Test
    void loadsLiveDimensionDetailFromConfiguredAloudataDatasource() {
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AloudataConfigHelper configHelper = mock(AloudataConfigHelper.class);
        AloudataApiClient apiClient = mock(AloudataApiClient.class);
        AloudataEndpointService endpointService = mock(AloudataEndpointService.class);
        AloudataSemanticSyncServiceImpl service = new AloudataSemanticSyncServiceImpl(
                mock(AloudataMetricMapper.class),
                mock(AloudataDimensionMapper.class),
                mock(AloudataMetricDimensionMapper.class),
                mock(AloudataCategoryMapper.class),
                datasourceMapper,
                apiClient,
                configHelper,
                endpointService,
                mock(AloudataSemanticEsService.class),
                mock(ModelConfigService.class),
                mock(AloudataService.class));
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setSourceType("aloudata");
        AloudataConfigDTO config = new AloudataConfigDTO();
        when(datasourceMapper.selectById(9L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(config);
        when(endpointService.buildParamsFromConfigAndInput(eq("dimension_detail"), eq(config), anyMap()))
                .thenReturn(new java.util.HashMap<>());
        when(apiClient.callWithParams(eq("dimension_detail"), eq(config), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "data", Map.of(
                        "dimensionId", "dim-id", "dimName", "region", "dimDisplayName", "所属大区",
                        "originDataType", "VARCHAR", "dimDescription", "区域维度",
                        "datasetName", "demo_inventory_detail"))));

        var result = service.getDimensionDetail(9L, "region");

        assertEquals("region", result.getDimName());
        assertEquals("所属大区", result.getDimDisplayName());
        assertEquals("VARCHAR", result.getOriginDataType());
        assertEquals("区域维度", result.getDimDescription());
        assertEquals("demo_inventory_detail", result.getDatasetName());
        verify(apiClient).callWithParams(eq("dimension_detail"), eq(config), anyMap());
    }

    @Test
    void loadsMetricDirectoryFromConfiguredAloudataDatasource() {
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AloudataConfigHelper configHelper = mock(AloudataConfigHelper.class);
        AloudataApiClient apiClient = mock(AloudataApiClient.class);
        AloudataSemanticSyncServiceImpl service = new AloudataSemanticSyncServiceImpl(
                mock(AloudataMetricMapper.class),
                mock(AloudataDimensionMapper.class),
                mock(AloudataMetricDimensionMapper.class),
                mock(AloudataCategoryMapper.class),
                datasourceMapper,
                apiClient,
                configHelper,
                mock(AloudataEndpointService.class),
                mock(AloudataSemanticEsService.class),
                mock(ModelConfigService.class),
                mock(AloudataService.class));
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setSourceType("aloudata");
        AloudataConfigDTO config = new AloudataConfigDTO();
        Map<String, Object> root = Map.of("categoryId", "cat-1", "categoryName", "策略解读",
                "metricList", List.of(Map.of("metricName", "conversion_count")), "subCategory", List.of());
        when(datasourceMapper.selectById(9L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(config);
        when(apiClient.callWithParams(eq("metric_tree"), eq(config), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "data", Map.of("rootList", List.of(root)))));

        assertEquals(List.of(root), service.listMetricDirectory(9L));
        verify(apiClient).callWithParams(eq("metric_tree"), eq(config), anyMap());
    }

    @Test
    void forwardsDimensionKeywordToDirectAloudataListApi() {
        DatasourceMapper datasourceMapper = mock(DatasourceMapper.class);
        AloudataConfigHelper configHelper = mock(AloudataConfigHelper.class);
        AloudataApiClient apiClient = mock(AloudataApiClient.class);
        AloudataEndpointService endpointService = mock(AloudataEndpointService.class);
        AloudataSemanticSyncServiceImpl service = new AloudataSemanticSyncServiceImpl(
                mock(AloudataMetricMapper.class),
                mock(AloudataDimensionMapper.class),
                mock(AloudataMetricDimensionMapper.class),
                mock(AloudataCategoryMapper.class),
                datasourceMapper,
                apiClient,
                configHelper,
                endpointService,
                mock(AloudataSemanticEsService.class),
                mock(ModelConfigService.class),
                mock(AloudataService.class));
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setSourceType("aloudata");
        AloudataConfigDTO config = new AloudataConfigDTO();
        when(datasourceMapper.selectById(9L)).thenReturn(datasource);
        when(configHelper.parseConfig(datasource)).thenReturn(config);
        when(endpointService.buildParamsFromConfigAndInput(eq("dimension_list"), eq(config), anyMap()))
                .thenReturn(new java.util.HashMap<>());
        when(apiClient.callWithParams(eq("dimension_list"), eq(config), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "data", Map.of("total", 0, "data", List.of()))));
        AloudataDimensionPageQuery query = new AloudataDimensionPageQuery();
        query.setPageNumber(1);
        query.setPageSize(20);
        query.setKeyword("metric_name");

        service.pageDimensions(9L, query);

        org.mockito.ArgumentCaptor<Map<String, Object>> params = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(apiClient).callWithParams(eq("dimension_list"), eq(config), params.capture());
        assertEquals("metric_name", params.getValue().get("keyword"));
    }

    @Test
    void acceptsNumericAloudataTimestampsWhenBuildingMetricEntities() {
        AloudataSemanticSyncServiceImpl service = new AloudataSemanticSyncServiceImpl(
                mock(AloudataMetricMapper.class),
                mock(AloudataDimensionMapper.class),
                mock(AloudataMetricDimensionMapper.class),
                mock(AloudataCategoryMapper.class),
                mock(DatasourceMapper.class),
                mock(AloudataApiClient.class),
                mock(AloudataConfigHelper.class),
                mock(AloudataEndpointService.class),
                mock(AloudataSemanticEsService.class),
                mock(ModelConfigService.class),
                mock(AloudataService.class));

        Map<String, Object> metric = Map.of(
                "metricName", "metric_a",
                "gmtCreate", 1758100000000L,
                "gmtUpdate", 1758100000000L);

        @SuppressWarnings("unchecked")
        List<AloudataMetricEntity> entities = (List<AloudataMetricEntity>) ReflectionTestUtils.invokeMethod(
                service, "buildMetricEntities", 1L, List.of(metric), Map.of(), Map.of(), 1);

        assertEquals("1758100000000", entities.get(0).getGmtCreate());
        assertEquals("1758100000000", entities.get(0).getGmtUpdate());
    }
}
