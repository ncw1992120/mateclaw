package vip.mate.dataagent.aloudata.local;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.aloudata.AloudataFilterExpressions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校验本地 mock 夹具本身：结构、条数、参数感知行为。
 * <p>
 * 夹具位于 {@code src/main/resources/mock/aloudata/}，由
 * {@code dev-support/local-simulation/scripts/generate-aloudata-fixtures.py}
 * 从 {@code generated_cljd_zcl.sql} 生成，这里只做契约断言，不连接任何外部服务。
 */
class LocalAloudataFixturesTest {

    private final LocalAloudataFixtures fixtures = new LocalAloudataFixtures();

    @Test
    @SuppressWarnings("unchecked")
    void metricTreeReturnsNestedMetricDirectoryForLocalDashboardEditing() {
        Map<String, Object> body = fixtures.payload("metric_tree", Map.of(), null);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> roots = (List<Map<String, Object>>) data.get("rootList");

        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals("策略解读", roots.getFirst().get("categoryName"));
        assertEquals("metric-strategy", roots.getFirst().get("categoryId"));
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) roots.getFirst().get("metricList");
        assertTrue(metrics.stream().anyMatch(metric -> "digo_cust_asset_in".equals(metric.get("metricName"))));
        List<Map<String, Object>> children = (List<Map<String, Object>>) roots.getFirst().get("subCategory");
        assertEquals("子策略", children.getFirst().get("categoryName"));
        assertEquals("metric-sub-strategy", children.getFirst().get("categoryId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void categoryListFiltersMetricAndDimensionDirectories() {
        Map<String, Object> body = fixtures.payload("category_list", Map.of("categoryType", "CATEGORY_DIMENSION"), null);
        List<Map<String, Object>> categories = (List<Map<String, Object>>) body.get("data");

        assertEquals(Boolean.TRUE, body.get("success"));
        assertTrue(categories.stream().allMatch(category -> "CATEGORY_DIMENSION".equals(category.get("categoryType"))));
        assertTrue(categories.stream().anyMatch(category -> "dim-sub-strategy".equals(category.get("id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectorMetricListSearchesFieldsAndPaginatesWithinCategory() {
        Map<String, Object> body = fixtures.payload("metric_list", Map.of(
                "keyword", "入金", "metricCategoryId", "metric-sub-strategy",
                "pageNumber", 1, "pageSize", 1), null);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("data");

        assertEquals(3, data.get("total"));
        assertEquals(1, records.size());
        assertEquals("digo_cust_asset_in", records.getFirst().get("metricName"));

        Map<String, Object> byField = fixtures.payload("metric_list", Map.of("keyword", "digo_strategy_cnt_distr_1"), null);
        List<Map<String, Object>> fieldResults = (List<Map<String, Object>>)
                ((Map<String, Object>) byField.get("data")).get("data");
        assertEquals(List.of("digo_strategy_cnt_distr_1"),
                fieldResults.stream().map(item -> item.get("metricName")).toList());

        Map<String, Object> allMetrics = fixtures.payload("metric_list", Map.of("pageNumber", 1, "pageSize", 100), null);
        assertEquals(19, ((Map<String, Object>) allMetrics.get("data")).get("total"));
        Map<String, Object> allDimensions = fixtures.payload("dimension_list", Map.of(
                "pager", Map.of("pageNumber", 1, "pageSize", 100)), null);
        assertEquals(13, ((Map<String, Object>) allDimensions.get("data")).get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void strategyMetricsExposeOnlyDimensionsFromTheirOwningView() {
        Map<String, Object> metricList = (Map<String, Object>) fixtures.payload("metric_list", Map.of(
                "pageNumber", 1, "pageSize", 100), null).get("data");
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) metricList.get("data");
        Set<String> metricNames = new HashSet<>();
        metrics.forEach(metric -> metricNames.add((String) metric.get("metricName")));

        Map<String, Object> dimensionList = (Map<String, Object>) fixtures.payload("dimension_list", Map.of(
                "pager", Map.of("pageNumber", 1, "pageSize", 100)), null).get("data");
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) dimensionList.get("data");
        Set<String> dimensionNames = new HashSet<>();
        dimensions.forEach(dimension -> dimensionNames.add((String) dimension.get("dimName")));

        Map<String, Object> body = fixtures.payload("metric_all_dimensions", Map.of(
                "metricNames", List.copyOf(metricNames)), null);
        Map<String, Object> relations = (Map<String, Object>) body.get("data");

        assertEquals(metricNames, relations.keySet());
        Set<String> zbDimensions = Set.of(
                "metric_time", "attribution_plan_id", "attribution_strategy_id", "platform_id", "channel");
        Set<String> wdDimensions = dimensionNames;
        Map<String, Object> zbViewEnvelope = fixtures.payload("analysis_view_query_by_name",
                Map.of("viewName", "cljd_zcl_zb_view"), null);
        Map<String, Object> wdViewEnvelope = fixtures.payload("analysis_view_query_by_name",
                Map.of("viewName", "cljd_zcl_wd_view"), null);
        Map<String, Object> zbView = (Map<String, Object>) zbViewEnvelope.get("data");
        Map<String, Object> wdView = (Map<String, Object>) wdViewEnvelope.get("data");
        Set<String> zbMetrics = new HashSet<>((List<String>) zbView.get("metrics"));
        Set<String> wdMetrics = new HashSet<>((List<String>) wdView.get("metrics"));

        relations.forEach((metricName, available) -> {
            Set<String> expected = zbMetrics.contains(metricName) ? zbDimensions
                    : wdMetrics.contains(metricName) ? wdDimensions : Set.of();
            assertFalse(expected.isEmpty(), "mock metric must belong to one strategy view: " + metricName);
            assertEquals(expected, new HashSet<>((List<String>) available), metricName);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricDimensionsReturnsTheRequestedMetricDimensionNames() {
        String metricName = "digo_touch_cnt_1";
        Map<String, Object> allRelations = fixtures.payload("metric_all_dimensions",
                Map.of("metricNames", List.of(metricName)), null);
        Map<String, Object> singleRelation = fixtures.payload("metric_dimensions",
                Map.of("metricNames", List.of(metricName)), null);

        assertEquals(Boolean.TRUE, singleRelation.get("success"));
        assertEquals(((Map<String, Object>) allRelations.get("data")).get(metricName), singleRelation.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dimensionDetailExposesFieldNameDisplayNameAndDescription() {
        Map<String, Object> body = fixtures.payload("dimension_detail", Map.of("dimName", "metric_name"), null);
        Map<String, Object> detail = (Map<String, Object>) body.get("data");

        assertEquals("metric_name", detail.get("dimName"));
        assertEquals("转化指标名称", detail.get("dimDisplayName"));
        assertEquals("转化指标名称", detail.get("dimDescription"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void treeReturnsTwoStrategyViewsUnderOneCategory() {
        Map<String, Object> body = fixtures.payload("analysis_view_tree", Map.of(), null);

        assertEquals("200", body.get("code"));
        assertEquals(Boolean.TRUE, body.get("success"));

        List<Map<String, Object>> roots = (List<Map<String, Object>>) ((Map<String, Object>) body.get("data"))
                .get("analysisViewRoots");
        assertEquals(1, roots.size());
        assertEquals("策略解读", roots.getFirst().get("categoryName"));

        List<Map<String, Object>> views = (List<Map<String, Object>>) roots.getFirst().get("analysisViewList");
        assertEquals(List.of("cljd_zcl_zb_view", "cljd_zcl_wd_view"),
                views.stream().map(view -> view.get("viewName")).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listViewsRewritesOwnerToCurrentAuthValueAndFiltersByKeyword() {
        Map<String, Object> body = fixtures.payload("analysis_view_list", Map.of("keyword", "_"), "uid-abc");
        List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<String, Object>) body.get("data")).get("data");
        assertEquals(2, items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> basic = (Map<String, Object>) item.get("basicAttributes");
            assertEquals("uid-abc", basic.get("owner"), "owner 必须等于数据源认证值，否则「只看我的」为空");
        }

        Map<String, Object> narrowed = fixtures.payload("analysis_view_list", Map.of("keyword", "zb"), null);
        List<Map<String, Object>> narrowedItems =
                (List<Map<String, Object>>) ((Map<String, Object>) narrowed.get("data")).get("data");
        assertEquals(List.of("cljd_zcl_zb_view"),
                narrowedItems.stream().map(item -> item.get("viewName")).toList());
    }

    /** 真实 queryByName 的 metrics/dimensions 是字符串数组，展示名在 displayNameMap。 */
    @Test
    @SuppressWarnings("unchecked")
    void viewDetailUsesStringArraysPlusDisplayNameMap() {
        Map<String, Object> body = fixtures.payload("analysis_view_query_by_name",
                Map.of("viewName", "cljd_zcl_zb_view"), "uid-abc");
        Map<String, Object> data = (Map<String, Object>) body.get("data");

        assertEquals("cljd_zcl_zb_view", data.get("viewName"));
        List<Object> metrics = (List<Object>) data.get("metrics");
        List<Object> dimensions = (List<Object>) data.get("dimensions");
        assertEquals(13, metrics.size());
        assertEquals(5, dimensions.size());
        assertInstanceOf(String.class, metrics.getFirst(), "metrics 必须是字符串数组（与真实响应一致）");
        assertInstanceOf(String.class, dimensions.getFirst());

        Map<String, Object> displayNameMap = (Map<String, Object>) data.get("displayNameMap");
        assertEquals("入金客户数", displayNameMap.get("digo_cust_asset_in"));
        assertEquals("触达渠道", displayNameMap.get("channel"));
        assertFalse(displayNameMap.values().stream().anyMatch(value -> String.valueOf(value).isBlank()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownViewReturnsViewAccessDenied() {
        Map<String, Object> body = fixtures.payload("analysis_view_query_by_name",
                Map.of("viewName", "not_exists"), null);

        assertEquals("SM_02_0038", body.get("code"));
        assertEquals(Boolean.FALSE, body.get("success"));
        assertNull(body.get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchDetailFiltersByRequestedMetricNames() {
        Map<String, Object> body = fixtures.payload("metric_batch_detail",
                Map.of("metricNames", List.of("digo_cust_asset_in", "digo_touch_cnt_1")), null);
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("data");

        assertEquals(List.of("digo_cust_asset_in", "digo_touch_cnt_1"),
                items.stream().map(item -> item.get("metricName")).toList());
        assertEquals("入金客户数", items.getFirst().get("metricDisplayName"));
        assertEquals("入金客户数_策略归因", items.getFirst().get("businessCaliber"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void touchMetricNamesAndUnitsMatchTheirCountSemanticsAcrossMockCatalogs() {
        Map<String, Object> batch = fixtures.payload("metric_batch_detail", Map.of(), null);
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) batch.get("data");
        Map<String, Map<String, Object>> byName = metrics.stream().collect(
                java.util.stream.Collectors.toMap(item -> (String) item.get("metricName"), item -> item));
        assertEquals("触达次数", byName.get("digo_touch_cnt_1").get("metricDisplayName"));
        assertEquals("次", byName.get("digo_touch_cnt_1").get("unit"));
        assertEquals("触达人数", byName.get("digo_touch_user_cnt_1").get("metricDisplayName"));
        assertEquals("人", byName.get("digo_touch_user_cnt_1").get("unit"));

        Map<String, Object> detail = fixtures.payload("analysis_view_query_by_name",
                Map.of("viewName", "cljd_zcl_wd_view"), null);
        Map<String, Object> view = (Map<String, Object>) detail.get("data");
        Map<String, String> displayNames = (Map<String, String>) view.get("displayNameMap");
        assertEquals("触达次数", displayNames.get("digo_touch_cnt_1"));
        assertEquals("触达人数", displayNames.get("digo_touch_user_cnt_1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dimensionListCarriesDisplayNameAndDescription() {
        Map<String, Object> body = fixtures.payload("dimension_list", Map.of(), null);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("data");

        assertEquals(13, data.get("total"));
        assertEquals(13, items.size());
        Map<String, Object> channel = items.stream()
                .filter(item -> "channel".equals(item.get("dimName"))).findFirst().orElseThrow();
        assertEquals("触达渠道", channel.get("dimDisplayName"));
        assertEquals("VARCHAR", channel.get("originDataType"));
    }

    @Test
    void dimensionValuesReturnsConversionMetricNamesFromStrategyDimensionView() {
        Map<String, Object> body = fixtures.payload("dimension_values", Map.of(
                "dimName", "metric_name",
                "dimValueKeyword", "加仓",
                "pageNumber", 1,
                "pageSize", 1), null);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> table = (Map<String, Object>) data.get("table");
        List<List<String>> rows = (List<List<String>>) table.get("metric_name");

        assertEquals("200", body.get("code"));
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(List.of(List.of("经纪个人场内公募非货加仓交易量")), rows);
    }

    @Test
    @SuppressWarnings("unchecked")
    void viewResultIsColumnarAndSupportsPaging() {
        Map<String, Object> body = fixtures.payload("analysis_view_query_data",
                Map.of("viewName", "cljd_zcl_zb_view", "pageSize", 2, "pageIndex", 1), null);
        Map<String, Object> table =
                (Map<String, Object>) ((Map<String, Object>) body.get("data")).get("table");
        Map<String, Object> columns = (Map<String, Object>) table.get("columns");

        assertEquals(18, columns.size(), "zb 视图应为 5 维度 + 13 指标");
        assertEquals(6L, table.get("total"));
        for (Object column : columns.values()) {
            assertEquals(2, ((List<?>) column).size(), "分页后每列应为 2 行");
        }
        // 第二个分页页（pageIndex=1）应为第 3、4 行
        List<Map<String, Object>> channel = (List<Map<String, Object>>) columns.get("channel");
        assertEquals("APP", channel.get(0).get("value"));
        assertEquals("APP", channel.get(1).get("value"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void wdViewProducesEighteenRowsAcrossNineteenColumns() {
        Map<String, Object> body = fixtures.payload("analysis_view_query_data",
                Map.of("viewName", "cljd_zcl_wd_view", "pageSize", 100, "pageIndex", 0), null);
        Map<String, Object> table =
                (Map<String, Object>) ((Map<String, Object>) body.get("data")).get("table");
        Map<String, Object> columns = (Map<String, Object>) table.get("columns");

        assertEquals(19, columns.size(), "wd 视图应为 13 维度 + 6 指标");
        assertEquals(18L, table.get("total"), "6 组 × 3 个 metric_id");
        for (Object column : columns.values()) {
            assertEquals(18, ((List<?>) column).size());
        }
        List<Map<String, Object>> metricId = (List<Map<String, Object>>) columns.get("metric_id");
        assertEquals("trd_fund_amt_inout_cy_jjgr", metricId.get(0).get("value"));
        assertEquals("pub_fh_kgdb_trdamt_ppcadd_jjgr", metricId.get(2).get("value"));
        assertEquals("trd_fund_amt_inout_cy_jjgr", metricId.get(3).get("value"));
    }

    /** metrics_query 无 viewName，应按请求字段反查视图，先筛选明细再按维度分组聚合。 */
    @Test
    @SuppressWarnings("unchecked")
    void metricsQueryProjectsColumnsAndAppliesEqFilter() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_cust_asset_in"),
                "dimensions", List.of("channel"),
                "filters", List.of("[channel] = \"APP\"")), null);
        Map<String, Object> table = (Map<String, Object>) ((Map<String, Object>) body.get("data")).get("table");
        Map<String, Object> columns = (Map<String, Object>) table.get("columns");

        assertEquals(List.of("channel", "digo_cust_asset_in"), List.copyOf(columns.keySet()));
        List<Map<String, Object>> channel = (List<Map<String, Object>>) columns.get("channel");
        assertEquals(1, channel.size(), "channel=APP 的明细应合并为一个分组");
        assertTrue(channel.stream().allMatch(cell -> "APP".equals(cell.get("value"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsQueryFiltersGroupsAndSumsLikeTheDashboardQuery() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of(
                        "digo_distr_count_1",
                        "digo_distr_user_cnt_a",
                        "digo_strategy_cnt_distr_1",
                        "digo_strategy_cnt"),
                "dimensions", List.of("metric_time"),
                "filters", List.of("([metric_time] >= \"2026-09-01\" AND [metric_time] < \"2026-09-02\")"),
                "limit", 100,
                "offset", 0,
                "isQueryTotalCount", true), null);

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> table = (Map<String, Object>) data.get("table");
        Map<String, List<Map<String, Object>>> columns = (Map<String, List<Map<String, Object>>>) table.get("columns");

        assertEquals(1, data.get("total"));
        assertEquals("2026-09-01", columns.get("metric_time").getFirst().get("value"));
        assertEquals(1470, ((Number) columns.get("digo_distr_count_1").getFirst().get("value")).intValue());
        assertEquals(1215, ((Number) columns.get("digo_distr_user_cnt_a").getFirst().get("value")).intValue());
        assertEquals(21, ((Number) columns.get("digo_strategy_cnt_distr_1").getFirst().get("value")).intValue());
        assertEquals(21, ((Number) columns.get("digo_strategy_cnt").getFirst().get("value")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsQueryAcceptsDashboardMetricTimeRangeExpression() {
        String lowerBound = AloudataFilterExpressions.of("metric_time", ">=", "2026-09-01", "DAY");
        String upperBound = AloudataFilterExpressions.of("metric_time", "<", "2026-09-02", "DAY");
        List<String> range = AloudataFilterExpressions.combineMetricTimeExpressions(
                List.of(lowerBound, upperBound));
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_distr_count_1"),
                "dimensions", List.of("metric_time"),
                "filters", range,
                "limit", 100,
                "offset", 0,
                "isQueryTotalCount", true), null);

        assertEquals(Boolean.TRUE, body.get("success"), body.toString());
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, List<Map<String, Object>>> columns = (Map<String, List<Map<String, Object>>>)
                ((Map<String, Object>) data.get("table")).get("columns");
        assertEquals(1, data.get("total"));
        assertEquals("2026-09-01", columns.get("metric_time").getFirst().get("value"));
        assertEquals(1470, ((Number) columns.get("digo_distr_count_1").getFirst().get("value")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsQueryMatchesHttpMockThreeDayConversionSamples() {
        List<String> metrics = List.of("digo_strategy_cnt_distr_1", "digo_distr_count_1",
                "digo_distr_user_cnt_a", "digo_touch_cnt_1", "digo_touch_user_cnt_1");
        List<String> names = List.of("经纪个人客户场内公募非货当年净买入", "经纪个人场内公募非货交易量",
                "经纪个人场内公募非货加仓交易量");
        List<List<Integer>> expected = List.of(
                List.of(21, 1470, 1215, 984, 789),
                List.of(30, 1560, 1287, 1038, 825),
                List.of(39, 1650, 1359, 1092, 861));

        for (int index = 0; index < names.size(); index++) {
            Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                    "metrics", metrics,
                    "dimensions", List.of(),
                    "filters", List.of(
                            "([metric_time] >= \"2026-09-01\" AND [metric_time] < \"2026-09-04\")",
                            "[metric_name] = \"" + names.get(index) + "\""),
                    "limit", 100), null);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            Map<String, List<Map<String, Object>>> columns = (Map<String, List<Map<String, Object>>>)
                    ((Map<String, Object>) data.get("table")).get("columns");
            assertEquals(Boolean.TRUE, body.get("success"), body.toString());
            assertEquals(expected.get(index), metrics.stream()
                    .map(metric -> ((Number) columns.get(metric).getFirst().get("value")).intValue()).toList());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsQueryCanFilterByADimensionOutsideTheReturnedProjection() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_strategy_cnt"),
                "dimensions", List.of("metric_time"),
                "filters", List.of(
                        "[metric_time] = \"2026-09-01\"",
                        "[attribution_strategy_id] = \"STR-002\"")), null);

        Map<String, Object> table = (Map<String, Object>)
                ((Map<String, Object>) body.get("data")).get("table");
        Map<String, List<Map<String, Object>>> columns =
                (Map<String, List<Map<String, Object>>>) table.get("columns");
        assertEquals("2026-09-01", columns.get("metric_time").getFirst().get("value"));
        assertEquals(9, ((Number) columns.get("digo_strategy_cnt").getFirst().get("value")).intValue());
    }

    /** 结构化 filters 是真实服务的非法形态（SM99002），本地必须同样失败，杜绝「本地假绿」。 */
    @Test
    void structuredFiltersAreRejectedLikeTheRealService() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_cust_asset_in"),
                "filters", List.of(Map.of("field", "channel", "operator", "eq", "value", "APP"))), null);

        assertEquals("SM99002", body.get("code"));
        assertEquals(false, body.get("success"));
        assertNull(body.get("data"));
    }

    @Test
    void unsupportedFilterExpressionIsRejectedInsteadOfBeingIgnored() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_cust_asset_in"),
                "filters", List.of("([channel] = \"APP\" OR [channel] = \"WAP\")")), null);

        assertEquals("SM99002", body.get("code"));
        assertEquals(false, body.get("success"));
        assertNull(body.get("data"));
    }

    @Test
    void metricsQueryDoesNotSplitAndInsideQuotedFilterValues() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_cust_asset_in"),
                "filters", List.of("[channel] = \"APP AND WEB\"")), null);

        assertEquals(Boolean.TRUE, body.get("success"), body.toString());
        assertEquals("200", body.get("code"));
    }

    /** 范围筛选编译成 `([f] >= "a" AND [f] <= "b")`，mock 必须能解析 AND 组合。 */
    @Test
    @SuppressWarnings("unchecked")
    void metricsQuerySupportsRangeAndInExpressions() {
        Map<String, Object> ranged = fixtures.payload("metrics_query", Map.of(
                "dimensions", List.of("metric_time"),
                "filters", List.of("([metric_time] >= \"2026-09-02\" AND [metric_time] <= \"2026-09-03\")")), null);
        Map<String, Object> columns = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>) ranged.get("data")).get("table")).get("columns");
        List<Map<String, Object>> times = (List<Map<String, Object>>) columns.get("metric_time");
        assertTrue(times.stream().allMatch(cell ->
                String.valueOf(cell.get("value")).compareTo("2026-09-02") >= 0
                        && String.valueOf(cell.get("value")).compareTo("2026-09-03") <= 0));

        Map<String, Object> inList = fixtures.payload("metrics_query", Map.of(
                "dimensions", List.of("channel"),
                "filters", List.of("[channel] IN (\"APP\",\"WAP\")")), null);
        Map<String, Object> inColumns = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>) inList.get("data")).get("table")).get("columns");
        List<Map<String, Object>> channels = (List<Map<String, Object>>) inColumns.get("channel");
        assertTrue(channels.stream().allMatch(cell ->
                "APP".equals(cell.get("value")) || "WAP".equals(cell.get("value"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsQuerySupportsTimeConstraintOrderingResultFilterAndTotalCount() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_cust_asset_in"),
                "dimensions", List.of("metric_time", "channel"),
                "timeConstraint", "([metric_time] >= \"2026-09-02\" AND [metric_time] <= \"2026-09-02\")",
                "resultFilters", List.of("[channel] <> \"SMS\""),
                "orders", List.of(Map.of("metric_time", "desc")),
                "limit", 1,
                "offset", 0,
                "isQueryTotalCount", true,
                "queryResultType", "SQL_AND_DATA",
                "source", "dashboard-card-001"), null);

        assertEquals(Boolean.TRUE, body.get("success"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, Object> table = (Map<String, Object>) data.get("table");
        Map<String, Object> columns = (Map<String, Object>) table.get("columns");
        assertEquals(1, ((List<?>) columns.get("metric_time")).size());
        assertEquals("2026-09-02", ((Map<?, ?>) ((List<?>) columns.get("metric_time")).getFirst()).get("value"));
        assertEquals(1, data.get("total"), "total 应为聚合、结果筛选后的分页前分组数");
        assertEquals("dashboard-card-001", data.get("source"));
        assertEquals("SQL_AND_DATA", data.get("queryResultType"));
        assertNotNull(data.get("sql"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsQueryIgnoresUnsupportedViewDateFunctionsButAppliesExplicitDateRange() {
        String viewDateRange = "(((dateTrunc(['metric_time'], \"DAY\")) >= "
                + "(DATEADD(DateTrunc(TODAY(), \"DAY\"), -(364), \"DAY\")))) AND "
                + "(((dateTrunc(['metric_time'], \"DAY\")) <= "
                + "(DATEADD(DateTrunc(TODAY(), \"DAY\"), 0, \"DAY\")))) AND "
                + "([metric_time] >= \"2026-09-01\" AND [metric_time] < \"2026-09-02\")";
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_distr_count_1"),
                "dimensions", List.of("metric_time"),
                "timeConstraint", viewDateRange), null);

        assertEquals(Boolean.TRUE, body.get("success"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Map<String, List<Map<String, Object>>> columns = (Map<String, List<Map<String, Object>>>)
                ((Map<String, Object>) data.get("table")).get("columns");
        assertEquals(1, columns.get("metric_time").size());
        assertEquals("2026-09-01", columns.get("metric_time").getFirst().get("value"));
        assertEquals(1470, ((Number) columns.get("digo_distr_count_1").getFirst().get("value")).intValue());
    }

    @Test
    void metricsQueryAcceptsAllDocumentedOptionalParameterShapes() {
        Map<String, Object> body = fixtures.payload("metrics_query", Map.of(
                "metrics", List.of("digo_cust_asset_in"),
                "metricDefinitions", Map.of("tmp_metric", Map.of("expression", "digo_cust_asset_in")),
                "specialMvConfig", Map.of("enable", true),
                "queryResultType", "DATA",
                "isQueryTotalCount", false,
                "source", "test-suite"), null);

        assertEquals("200", body.get("code"));
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals("test-suite", ((Map<?, ?>) body.get("data")).get("source"));
    }

    @Test
    void unknownEndpointReturnsEmptyEnvelopeInsteadOfFailing() {
        Map<String, Object> body = fixtures.payload("no_such_endpoint", Map.of(), null);

        // 真实环境打到未注册端点是失败（404 语义），mock 不能伪装成「成功但无数据」
        assertEquals("SM_04_0004", body.get("code"));
        assertEquals(false, body.get("success"));
        assertNotNull(body.get("traceId"));
    }
}
