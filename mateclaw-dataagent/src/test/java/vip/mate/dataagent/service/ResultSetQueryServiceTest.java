package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.ObjectRef;
import vip.mate.dataagent.dto.QueryContextDTO;
import vip.mate.dataagent.dto.ResultPreviewRequest;
import vip.mate.dataagent.dto.FinalResultQueryConfigDTO;
import vip.mate.dataagent.model.DashboardExecutionEntity;
import vip.mate.dataagent.objectref.DatasetBatchCodec;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.repository.DashboardExecutionMapper;
import vip.mate.dataagent.service.code.ScriptResultContractService;
import vip.mate.dataagent.service.impl.ResultSetQueryServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 结果集查询测试（实施计划任务 7）：真实分页/总数、排序、错误码、旧接口语义不受影响。 */
class ResultSetQueryServiceTest {

    private DashboardExecutionMapper mapper2;
    private ObjectRefService objectRefs;
    private ResultSetQueryService service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper2 = mock(DashboardExecutionMapper.class);
        objectRefs = mock(ObjectRefService.class);
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        service = new ResultSetQueryServiceImpl(mapper2, guard, om, new ScriptResultContractService(),
                objectRefs, "0");
    }

    private DashboardExecutionEntity execution(String status, String outputJson, String outputRefJson) {
        DashboardExecutionEntity entity = new DashboardExecutionEntity();
        entity.setExecutionId("exec-1");
        entity.setWorkspaceId(7L);
        entity.setStatus(status);
        entity.setOutputJson(outputJson);
        entity.setOutputRefJson(outputRefJson);
        when(mapper2.selectOne(any())).thenReturn(entity);
        return entity;
    }

    private String inlineTable(int rowCount) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) rows.add(Map.of("id", i, "v", rowCount - i));
        try {
            return om.writeValueAsString(Map.of(
                    "schemaVersion", "1.0", "kind", "table",
                    "data", Map.of("columns", List.of(Map.of("name", "id", "title", "id", "dataType", "number", "nullable", false),
                            Map.of("name", "v", "title", "v", "dataType", "number", "nullable", false)), "rows", rows),
                    "meta", Map.of("rowCount", rowCount, "truncated", false, "sourceInputs", List.of("orders"))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("大结果真实分页：第 2 页返回精确 totalCount 与正确切片")
    void realPaginationWithTotalCount() throws Exception {
        execution("SUCCEEDED", inlineTable(25), null);
        Map<String, Object> response = service.preview("exec-1",
                new ResultPreviewRequest(null, new QueryContextDTO.PaginationSpec(2, 10), Map.of(), "req-1"));
        assertEquals(25, ((Number) response.get("totalCount")).intValue());
        assertEquals(2, ((Number) response.get("page")).intValue());
        assertEquals(10, ((Number) response.get("pageSize")).intValue());
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(10, rows.size()); // 25 行第 2 页 = 10 行
        assertEquals("req-1", response.get("requestId"));
    }

    @Test
    @DisplayName("服务端排序后分页：降序取值正确")
    void serverSideSortThenPage() throws Exception {
        execution("SUCCEEDED", inlineTable(5), null);
        Map<String, Object> response = service.preview("exec-1",
                new ResultPreviewRequest(new QueryContextDTO.SortSpec("v", "asc"), new QueryContextDTO.PaginationSpec(1, 2), Map.of(), null));
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(1, ((Number) ((Map<?, ?>) rows.get(0)).get("v")).intValue());
        assertEquals(5, ((Number) response.get("totalCount")).intValue());
    }

    @Test
    @DisplayName("配置最终结果查询时：筛选发生在 Python 输出之后")
    void finalResultQueryRunsOnValidatedPythonEnvelope() throws Exception {
        execution("SUCCEEDED", inlineTable(5), null);
        var config = new FinalResultQueryConfigDTO("schema-1", List.of(),
                List.of(new FinalResultQueryConfigDTO.FilterField("v", "值", "number", "min", List.of("gte"))),
                List.of(new FinalResultQueryConfigDTO.ParameterBinding("min", "v", "gte")),
                new FinalResultQueryConfigDTO.SortPolicy(true, List.of("v")),
                new FinalResultQueryConfigDTO.PaginationPolicy(true, 10, 100, true));

        Map<String, Object> response = service.preview("exec-1", new ResultPreviewRequest(
                new QueryContextDTO.SortSpec("v", "desc"),
                new QueryContextDTO.PaginationSpec(1, 2),
                Map.of("min", 3), "req-final", config));

        assertEquals(3, ((Number) response.get("totalCount")).intValue());
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(5, ((Number) ((Map<?, ?>) rows.get(0)).get("v")).intValue());
        assertEquals(4, ((Number) ((Map<?, ?>) rows.get(1)).get("v")).intValue());
    }

    @Test
    @DisplayName("未就绪/失败映射稳定错误码")
    void statusErrorCodes() {
        execution("RUNNING", null, null);
        var notReady = assertThrows(QueryPlanException.class,
                () -> service.preview("exec-1", null));
        assertEquals(QueryPlanErrorCodes.RESULT_NOT_READY, notReady.getCode());

        execution("FAILED", null, null);
        var failed = assertThrows(QueryPlanException.class,
                () -> service.preview("exec-1", null));
        assertEquals(QueryPlanErrorCodes.QUERY_CONTEXT_INVALID, failed.getCode());
        assertFalse(failed.getMessage().contains("jdbc:")); // 不透出连接串
    }

    @Test
    @DisplayName("跨 workspace 执行不可见")
    void crossWorkspaceDenied() {
        DashboardExecutionEntity entity = new DashboardExecutionEntity();
        entity.setExecutionId("exec-1");
        entity.setWorkspaceId(99L);
        entity.setStatus("SUCCEEDED");
        when(mapper2.selectOne(any())).thenReturn(entity);
        assertThrows(QueryPlanException.class, () -> service.preview("exec-1", null));
    }

    @Test
    @DisplayName("ObjectRef 大结果：受控分批读全量后分页；过期引用显式失败")
    void objectRefPagination() throws Exception {
        // 构造 12 行的 parquet 引用
        List<Map<String, Object>> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) all.add(Map.of("v", i));
        var refs = Mockito.mockStatic(DatasetBatchCodec.class);
        try {
            ObjectRef reference = new ObjectRef("obj-1", 7L, "task", "parquet", "digest", System.currentTimeMillis() + 60_000);
            DashboardExecutionEntity entity = execution("SUCCEEDED", null,
                    om.writeValueAsString(Map.of("objectId", "obj-1", "workspaceId", 7L, "taskId", "task",
                            "format", "parquet", "digest", "digest", "expiresAt", System.currentTimeMillis() + 60_000L,
                            "schemaVersion", "1.0", "kind", "table", "rowCount", 12,
                            "columns", List.of(Map.of("name", "v", "title", "v", "dataType", "number", "nullable", false)))));
            ObjectRef parsed = om.readValue(entity.getOutputRefJson(), ObjectRef.class);
            refs.when(() -> DatasetBatchCodec.readRows(any(), eq(parsed), eq(objectRefs), eq(0), eq(10_000)))
                    .thenReturn(all);
            Map<String, Object> response = service.preview("exec-1",
                    new ResultPreviewRequest(null, new QueryContextDTO.PaginationSpec(2, 10), Map.of(), null));
            assertEquals(12, ((Number) response.get("totalCount")).intValue());
            assertEquals(2, ((List<?>) response.get("rows")).size());
        } finally {
            refs.close();
        }

        // 过期/损坏引用：可解释失败
        execution("SUCCEEDED", null, "{invalid");
        var broken = assertThrows(QueryPlanException.class, () -> service.preview("exec-1", null));
        assertTrue(broken.getMessage().contains("unavailable"));
    }
}
