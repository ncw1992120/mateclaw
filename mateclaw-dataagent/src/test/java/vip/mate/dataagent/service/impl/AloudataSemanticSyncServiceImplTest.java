package vip.mate.dataagent.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.dto.AloudataConfigDTO;
import vip.mate.dataagent.dto.AloudataDimensionPageQuery;
import vip.mate.dataagent.dto.AloudataMetricPageQuery;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.AloudataCategoryMapper;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataSemanticEsService;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.support.AloudataSyncFilterSupport;
import vip.mate.dataagent.util.NameMatchSupport;
import vip.mate.llm.service.ModelConfigService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Aloudata 语义层同步服务单测：搜索关键字空格收口。
 * <p>
 * 背景：平台展示名/用户输入可能带首尾空格（含全角 U+3000、不间断空格 U+00A0），
 * 实时检索转发与本地 LIKE 检索前必须归一，否则「完整名搜不到、删掉末字符反而命中」。
 */
class AloudataSemanticSyncServiceImplTest {

    private AloudataMetricMapper metricMapper;
    private AloudataDimensionMapper dimensionMapper;
    private AloudataMetricDimensionMapper metricDimensionMapper;
    private AloudataCategoryMapper categoryMapper;
    private DatasourceMapper datasourceMapper;
    private AloudataApiClient apiClient;
    private AloudataConfigHelper configHelper;
    private AloudataEndpointService endpointService;
    private AloudataSemanticSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        metricMapper = mock(AloudataMetricMapper.class);
        dimensionMapper = mock(AloudataDimensionMapper.class);
        metricDimensionMapper = mock(AloudataMetricDimensionMapper.class);
        categoryMapper = mock(AloudataCategoryMapper.class);
        datasourceMapper = mock(DatasourceMapper.class);
        apiClient = mock(AloudataApiClient.class);
        configHelper = mock(AloudataConfigHelper.class);
        endpointService = mock(AloudataEndpointService.class);
        service = new AloudataSemanticSyncServiceImpl(
                metricMapper, dimensionMapper, metricDimensionMapper, categoryMapper,
                datasourceMapper, apiClient, configHelper, endpointService,
                mock(AloudataSemanticEsService.class), mock(ModelConfigService.class),
                mock(AloudataService.class), mock(AloudataSyncFilterSupport.class));
    }

    private void stubPlatformCall() {
        DatasourceEntity ds = new DatasourceEntity();
        ds.setSourceType("aloudata");
        when(datasourceMapper.selectById(any())).thenReturn(ds);
        when(configHelper.parseConfig(any(DatasourceEntity.class))).thenReturn(new AloudataConfigDTO());
        when(endpointService.buildParamsFromConfigAndInput(anyString(), any(), anyMap()))
                .thenAnswer(invocation -> new HashMap<>(invocation.getArgument(2)));
        when(apiClient.callWithParams(anyString(), any(), any()))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "data", Map.of("total", 0, "data", List.of()))));
    }

    @Test
    void pageMetricsTrimsKeywordBeforeForwarding() {
        stubPlatformCall();
        AloudataMetricPageQuery query = new AloudataMetricPageQuery();
        query.setKeyword(" \u3000经纪个人客户场内公募非货当年净买入\u00A0");

        service.pageMetrics(1L, query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(apiClient).callWithParams(eq("metric_list"), any(), captor.capture());
        assertEquals("经纪个人客户场内公募非货当年净买入", captor.getValue().get("keyword"));
    }

    @Test
    void pageMetricsKeepsBlankKeywordAbsent() {
        stubPlatformCall();
        AloudataMetricPageQuery query = new AloudataMetricPageQuery();
        query.setKeyword("  \u3000 ");

        service.pageMetrics(1L, query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(apiClient).callWithParams(eq("metric_list"), any(), captor.capture());
        assertNull(captor.getValue().get("keyword"));
    }

    @Test
    void pageDimensionsTrimsKeywordBeforeForwarding() {
        stubPlatformCall();
        AloudataDimensionPageQuery query = new AloudataDimensionPageQuery();
        query.setKeyword(" \u3000渠道\u00A0");

        service.pageDimensions(1L, query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(apiClient).callWithParams(eq("dimension_list"), any(), captor.capture());
        assertEquals("渠道", captor.getValue().get("keyword"));
    }

    @Test
    void syncIngestTrimsDisplayNamesAndSynonyms() {
        Map<String, Object> metricData = new HashMap<>();
        metricData.put("metricName", "digo_metric");
        metricData.put("metricDisplayName", " \u3000经纪个人客户场内公募非货当年净买入\u00A0");
        Map<String, Object> detail = new HashMap<>();
        detail.put("synonyms", java.util.Arrays.asList(" 净买入 ", "\u3000", "公募非货", null));
        Map<String, Map<String, Object>> detailMap = Map.of("digo_metric", detail);

        List<AloudataMetricEntity> entities = invokeBuildMetricEntities(List.of(metricData), detailMap);

        assertEquals(1, entities.size());
        assertEquals("经纪个人客户场内公募非货当年净买入", entities.get(0).getMetricDisplayName());
        assertEquals("净买入,公募非货", entities.get(0).getSynonyms());
    }

    @SuppressWarnings("unchecked")
    private List<AloudataMetricEntity> invokeBuildMetricEntities(List<Map<String, Object>> pageMetrics,
                                                                  Map<String, Map<String, Object>> detailMap) {
        try {
            var method = AloudataSemanticSyncServiceImpl.class.getDeclaredMethod("buildMetricEntities",
                    Long.class, List.class, Map.class, Map.class, int.class);
            method.setAccessible(true);
            return (List<AloudataMetricEntity>) method.invoke(service, 1L, pageMetrics, detailMap, Map.of(), 1);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void pageMetricsFallsBackToLocalSyncTableWhenPlatformEmpty() {
        stubPlatformCall();
        AloudataMetricEntity local = new AloudataMetricEntity();
        local.setDatasourceId(1L);
        local.setMetricName("digo_metric");
        local.setMetricDisplayName("经纪个人客户场内公募非货当年净买入");
        when(metricMapper.selectList(any())).thenReturn(List.of(local));

        AloudataMetricPageQuery query = new AloudataMetricPageQuery();
        query.setKeyword("经纪个人客户场内公募非货当年净买入");

        var page = service.pageMetrics(1L, query);

        assertEquals(1, page.getRecords().size());
        assertEquals("digo_metric", page.getRecords().get(0).getMetricName());
        org.mockito.Mockito.verify(apiClient).callWithParams(eq("metric_list"), any(), any());
    }

    @Test
    void trimSpacesCoversHalfWidthFullWidthAndNbsp() {
        assertEquals("净买入", NameMatchSupport.trimSpaces(" \u3000净买入\u00A0"));
        assertEquals("净买入", NameMatchSupport.trimSpaces("\uFEFF净买入 "));
        assertTrue(NameMatchSupport.trimSpaces("\u3000 \u00A0").isBlank());
        assertNull(NameMatchSupport.trimSpaces(null));
        assertEquals("", NameMatchSupport.trimSpaces(""));
    }
}
