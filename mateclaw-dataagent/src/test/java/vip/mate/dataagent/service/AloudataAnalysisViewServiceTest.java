package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataAnalysisViewField;
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

    /**
     * 真实 queryByName 的 metrics/dimensions 是**字符串数组**（实测 demo 租户），
     * 展示名单独放在 displayNameMap。字段列表必须据此归还「字段名 / 展示名 / 描述」。
     */
    @Test
    void listFieldsResolvesNamesFromStringArraysAndDetailEndpoints() {
        AloudataAnalysisViewServiceImpl service = ready();
        stubQueryByName(realisticDetail());

        when(apiClient.callWithParams(eq("metric_batch_detail"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("code", "200", "success", true, "data", List.of(
                        Map.of("metricName", "digo_cust_asset_in", "metricDisplayName", "入金客户数",
                                "businessCaliber", "入金客户数_策略归因"),
                        Map.of("metricName", "digo_new_cust_asset_in", "metricDisplayName", "新客入金数",
                                "businessCaliber", "新客入金数_策略归因")))));
        when(apiClient.callWithParams(eq("dimension_list"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("code", "200", "success", true, "data", Map.of(
                        "total", 2, "hasNext", false, "data", List.of(
                                Map.of("dimName", "metric_time", "dimDisplayName", "指标日期", "dimDescription", "指标日期"),
                                Map.of("dimName", "channel", "dimDisplayName", "触达渠道", "dimDescription", "触达渠道"))))));

        var fields = service.listFields(9L, "cljd_zcl_zb_view");

        // 顺序恒为「先指标后维度」
        assertEquals(List.of("digo_cust_asset_in", "digo_new_cust_asset_in", "metric_time", "channel"),
                fields.stream().map(AloudataAnalysisViewField::name).toList());
        assertEquals(List.of("measure", "measure", "dimension", "dimension"),
                fields.stream().map(AloudataAnalysisViewField::role).toList());
        // 描述：指标取业务口径，维度取维度描述
        assertEquals("入金客户数_策略归因", fields.get(0).description());
        assertEquals("指标日期", fields.get(2).description());
        // 展示名来自各详情接口，且不得出现 "{name=...}" 之类的脏串
        assertEquals("入金客户数", fields.get(0).displayName());
        assertEquals("触达渠道", fields.get(3).displayName());
        for (AloudataAnalysisViewField field : fields) {
            assertNotNull(field.displayName(), "展示名不应为空: " + field.name());
            assertNotNull(field.description(), "描述不应为空: " + field.name());
            assertFalse(field.name().contains("{"), "字段名不应是 Map 的 toString: " + field.name());
        }
    }

    /** 字符串数组 + displayNameMap 必须能构造出视图详情（修复前此处抛 Jackson 异常）。 */
    @Test
    void getByNameNormalizesStringArraysUsingDisplayNameMap() {
        AloudataAnalysisViewServiceImpl service = ready();
        stubQueryByName(realisticDetail());

        var view = service.getByName(9L, "cljd_zcl_zb_view");

        assertEquals("cljd_zcl_zb_view", view.viewName());
        assertEquals(List.of("digo_cust_asset_in", "digo_new_cust_asset_in"),
                view.metrics().stream().map(m -> m.get("name")).toList());
        assertEquals("入金客户数", view.metrics().get(0).get("displayName"));
        assertEquals(List.of("metric_time", "channel"),
                view.dimensions().stream().map(d -> d.get("name")).toList());
        assertEquals("指标日期", view.dimensions().get(0).get("displayName"));
    }

    /** 兼容旧的对象数组形态（历史夹具/部分环境），对象里的键不能被覆盖。 */
    @Test
    void getByNameKeepsObjectArrayDefinitions() {
        AloudataAnalysisViewServiceImpl service = ready();
        stubQueryByName(new LinkedHashMap<>(Map.of(
                "code", "200", "success", true, "data", Map.of(
                        "viewName", "sales_view",
                        "metrics", List.of(Map.of("name", "revenue", "displayName", "收入", "dataType", "DECIMAL")),
                        "dimensions", List.of(Map.of("name", "region", "displayName", "区域"))))));

        var view = service.getByName(9L, "sales_view");

        assertEquals("revenue", view.metrics().get(0).get("name"));
        assertEquals("收入", view.metrics().get(0).get("displayName"));
        assertEquals("DECIMAL", view.metrics().get(0).get("dataType"));
        assertEquals("区域", view.dimensions().get(0).get("displayName"));
    }

    /** 对象数组形态下字段列表同样要取到字段名，而不是 Map 的 toString。 */
    @Test
    void listFieldsToleratesObjectArrayDefinitions() {
        AloudataAnalysisViewServiceImpl service = ready();
        stubQueryByName(new LinkedHashMap<>(Map.of(
                "code", "200", "success", true, "data", Map.of(
                        "viewName", "sales_view",
                        "metrics", List.of(Map.of("name", "revenue", "displayName", "收入")),
                        "dimensions", List.of()))));
        when(apiClient.callWithParams(eq("metric_batch_detail"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(Map.of("code", "200", "success", true, "data", List.of(
                        Map.of("metricName", "revenue", "metricDisplayName", "收入", "businessCaliber", "收入口径")))));

        var fields = service.listFields(9L, "sales_view");

        assertEquals(1, fields.size());
        assertEquals("revenue", fields.getFirst().name());
        assertEquals("收入", fields.getFirst().displayName());
        assertEquals("收入口径", fields.getFirst().description());
    }

    /** 数据源与认证配置的通用打桩；返回可直接调用的 service。 */
    private AloudataAnalysisViewServiceImpl ready() {
        AloudataAnalysisViewServiceImpl service = service();
        DatasourceEntity ds = new DatasourceEntity();
        ds.setId(9L);
        ds.setSourceType("aloudata");
        when(datasourceMapper.selectById(9L)).thenReturn(ds);
        when(configHelper.parseConfig(ds)).thenReturn(new AloudataConfigDTO());
        return service;
    }

    private void stubQueryByName(Map<String, Object> body) {
        when(apiClient.callWithParams(eq("analysis_view_query_by_name"), any(), anyMap()))
                .thenReturn(ResponseEntity.ok(body));
    }

    /** 与真实 Aloudata 同构的视图详情：metrics/dimensions 为字符串数组 + displayNameMap。 */
    private Map<String, Object> realisticDetail() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 11);
        data.put("viewName", "cljd_zcl_zb_view");
        data.put("displayName", "策略解读-子策略-指标");
        data.put("metrics", List.of("digo_cust_asset_in", "digo_new_cust_asset_in"));
        data.put("dimensions", List.of("metric_time", "channel"));
        data.put("displayNameMap", Map.of(
                "digo_cust_asset_in", "入金客户数",
                "digo_new_cust_asset_in", "新客入金数",
                "metric_time", "指标日期",
                "channel", "触达渠道"));
        data.put("filters", List.of());
        data.put("resultFilters", List.of());
        data.put("orders", List.of());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "200");
        body.put("success", true);
        body.put("data", data);
        return body;
    }

    private AloudataAnalysisViewServiceImpl service() {
        return new AloudataAnalysisViewServiceImpl(datasourceMapper, datasourceManageService,
                configHelper, apiClient, new ObjectMapper());
    }
}
