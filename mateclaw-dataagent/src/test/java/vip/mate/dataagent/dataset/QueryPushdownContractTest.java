package vip.mate.dataagent.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.file.FileDatasetAdapter;
import vip.mate.dataagent.dataset.http.HttpApiDatasetAdapter;
import vip.mate.dataagent.dataset.jdbc.CompiledJdbcQuery;
import vip.mate.dataagent.dataset.jdbc.JSqlParserValidationService;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.service.AloudataService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 查询下推契约测试（实施计划任务 3）：
 * 第 2 页 offset 100、gte/lt 时间约束、总数关闭时不请求 count、
 * 不支持下推时排序语义不丢失（有界残余），以及 SQL 排序字段安全。
 */
class QueryPushdownContractTest {

    private final JSqlParserValidationService sqlService = new JSqlParserValidationService();

    // ==================== DatasetSort / DatasetReadRequest 契约 ====================

    @Test
    @DisplayName("DatasetSort 校验方向与字段；旧 7 参构造保持兼容")
    void datasetSortContract() {
        assertEquals("desc", new DatasetSort("in_account", "DESC").direction());
        assertThrows(IllegalArgumentException.class, () -> new DatasetSort("f", "sideways"));
        assertThrows(IllegalArgumentException.class, () -> new DatasetSort("", "asc"));
        // 旧构造：无排序、不请求总数
        DatasetReadRequest legacy = new DatasetReadRequest(1L, "x", List.of(), List.of(), 10, 0, Map.of());
        assertTrue(legacy.orders().isEmpty());
        assertFalse(legacy.requestTotalCount());
    }

    @Test
    @DisplayName("PushdownReport 新字段默认值：旧 5 参构造不声称排序/总数下推")
    void pushdownReportBackwardCompat() {
        PushdownReport report = new PushdownReport(List.of(), List.of(), true, true, "digest");
        assertTrue(report.ordersPushed().isEmpty());
        assertFalse(report.totalCountRequested());
    }

    // ==================== JDBC：排序安全拼接与 offset 换算 ====================

    @Test
    @DisplayName("JDBC 第 2 页 offset 100 与 ORDER BY 安全追加")
    void jdbcSortAndOffset() {
        CompiledJdbcQuery query = sqlService.compile("SELECT * FROM t", List.of("metric_date", "in_account"),
                List.of(new DatasetFilter("metric_date", "dimension", "gte", "2026-09-01"),
                        new DatasetFilter("metric_date", "dimension", "lt", "2026-10-01")),
                List.of(new DatasetSort("in_account", "desc")), 100, 100);
        assertTrue(query.sql().contains("ORDER BY in_account DESC"));
        assertTrue(query.sql().contains("metric_date >= ?"));
        assertTrue(query.sql().contains("metric_date < ?"));
        assertTrue(query.sql().contains("LIMIT ? OFFSET ?"));
        // 参数顺序：filters 两个 + limit + offset
        assertEquals(List.of("2026-09-01", "2026-10-01", 100, 100), query.parameters());
    }

    @Test
    @DisplayName("JDBC 排序字段不在注册表白名单时失败；非法标识符失败")
    void jdbcSortFieldSafety() {
        assertThrows(IllegalArgumentException.class, () -> sqlService.compile("SELECT * FROM t",
                List.of("a", "b"), List.of(),
                List.of(new DatasetSort("hacker_field", "asc")), 10, 0));
        assertThrows(IllegalArgumentException.class, () -> sqlService.compile("SELECT * FROM t",
                List.of("a"), List.of(),
                List.of(new DatasetSort("a; DROP TABLE t", "asc")), 10, 0));
    }

    // ==================== Aloudata：表达式与 orders/isQueryTotalCount ====================

    @Test
    @DisplayName("Aloudate gte/lt 时间约束与 in 多选转真实表达式；总数开关透传")
    void aloudataOrdersAndTotalCount() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("m");
        dataset.setSourceType("ALOUDATA_METRICS");
        dataset.setDatasourceId(3L);
        dataset.setSourceConfig("{\"dimensions\":[\"strategy_id\"],\"metrics\":[\"in_account\"]}");
        when(mapper.selectById(7L)).thenReturn(dataset);
        AloudataMetricQueryResponse response = new AloudataMetricQueryResponse();
        response.setCode("200");
        response.setData(null);
        when(service.queryMetrics(eq(3L), any())).thenReturn(response);

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, service, new com.fasterxml.jackson.databind.ObjectMapper());
        List<DatasetFilter> filters = List.of(
                new DatasetFilter("strategy_id", "dimension", "in", List.of("A", "B")),
                new DatasetFilter("metric_date", "dimension", "gte", "2026-09-01"),
                new DatasetFilter("metric_date", "dimension", "lt", "2026-10-01"));
        adapter.read(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)),
                new DatasetReadRequest(7L, "m", List.of(), filters,
                        List.of(new DatasetSort("in_account", "desc")), 100, 100, Map.of(), true));

        org.mockito.ArgumentCaptor<AloudataMetricQueryRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AloudataMetricQueryRequest.class);
        org.mockito.Mockito.verify(service).queryMetrics(eq(3L), captor.capture());
        AloudataMetricQueryRequest sent = captor.getValue();
        assertTrue(sent.getFilters().contains("[strategy_id] IN (\"A\",\"B\")"));
        assertTrue(sent.getFilters().contains("[metric_date] >= (\"2026-09-01\")"));
        assertTrue(sent.getFilters().contains("[metric_date] < (\"2026-10-01\")"));
        assertEquals(100, sent.getLimit());
        assertEquals(100, sent.getOffset());
        assertEquals(Boolean.TRUE, sent.getIsQueryTotalCount());
        assertEquals(List.of(Map.of("in_account", "desc")), sent.getOrders());
    }

    @Test
    @DisplayName("Aloudata 排序字段不在已选指标/维度中时不下发（残余），总数关闭时不请求 count")
    void aloudataResidualOrderAndNoCount() {
        DatasetMapper mapper = mock(DatasetMapper.class);
        AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("m");
        dataset.setSourceType("ALOUDATA_METRICS");
        dataset.setDatasourceId(3L);
        dataset.setSourceConfig("{\"dimensions\":[\"strategy_id\"],\"metrics\":[\"in_account\"]}");
        when(mapper.selectById(7L)).thenReturn(dataset);
        when(service.queryMetrics(eq(3L), any())).thenReturn(new AloudataMetricQueryResponse());
        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, service, new com.fasterxml.jackson.databind.ObjectMapper());
        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)),
                new DatasetReadRequest(7L, "m", List.of(), List.of(),
                        List.of(new DatasetSort("computed_field", "desc")), 100, 0, Map.of(), false));
        org.mockito.ArgumentCaptor<AloudataMetricQueryRequest> captor =
                org.mockito.ArgumentCaptor.forClass(AloudataMetricQueryRequest.class);
        org.mockito.Mockito.verify(service).queryMetrics(eq(3L), captor.capture());
        assertNull(captor.getValue().getOrders());
        assertEquals(Boolean.FALSE, captor.getValue().getIsQueryTotalCount());
        // 排序未下推但有界残余不丢语义：报告不声称排序已下推
        assertTrue(batch.pushdownReport().ordersPushed().isEmpty());
    }

    // ==================== HTTP / File：有界残余排序不丢语义 ====================

    @Test
    @DisplayName("ResidualRowOperations 排序与切片语义")
    void residualOperations() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("v", 3, "n", "c")));
        rows.add(new LinkedHashMap<>(Map.of("v", 1, "n", "a")));
        rows.add(new LinkedHashMap<>(Map.of("v", 2, "n", "b")));
        ResidualRowOperations.sort(rows, List.of(new DatasetSort("v", "asc")));
        assertEquals(List.of(1, 2, 3), rows.stream().map(r -> ((Number) r.get("v")).intValue()).toList());
        List<Map<String, Object>> page2 = new ArrayList<>(ResidualRowOperations.paginate(rows, 2, 2));
        assertEquals(1, page2.size());
        assertEquals(3, ((Number) page2.get(0).get("v")).intValue());
        assertTrue(ResidualRowOperations.paginate(rows, 2, 100).isEmpty());
    }

    @Test
    @DisplayName("ResidualRowOperations 降序与 null 值语义稳定")
    void residualSortDescAndNulls() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("v", 1)));
        Map<String, Object> nullRow = new LinkedHashMap<>();
        nullRow.put("v", null);
        rows.add(nullRow);
        rows.add(new LinkedHashMap<>(Map.of("v", 5)));
        ResidualRowOperations.sort(rows, List.of(new DatasetSort("v", "desc")));
        // nullsLast 升序翻转后：降序 null 排最前，数值降序在后（语义稳定）
        assertNull(rows.get(0).get("v"));
        assertEquals(5, ((Number) rows.get(1).get("v")).intValue());
        assertEquals(1, ((Number) rows.get(2).get("v")).intValue());
    }
}
