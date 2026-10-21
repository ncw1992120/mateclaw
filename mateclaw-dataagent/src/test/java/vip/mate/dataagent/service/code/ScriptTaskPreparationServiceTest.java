package vip.mate.dataagent.service.code;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.dataset.demo.StrategyReadoutDemoDatasetFixtures;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.service.DatasetManageService;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScriptTaskPreparationServiceTest {
    @Test void materializesIndependentInputsConcurrentlyAndKeepsInputOrder() {
        DatasetManageService datasets = mock(DatasetManageService.class);
        DatasetSourceAdapter adapter = mock(DatasetSourceAdapter.class);
        when(adapter.supports(DatasetSourceType.JDBC_TABLE)).thenReturn(true);
        for (long datasetId : List.of(3L, 4L)) {
            when(datasets.getInputDescriptor(any(), eq(datasetId), any()))
                    .thenAnswer(invocation -> new DatasetInputDescriptor(datasetId,
                            invocation.getArgument(2), DatasetSourceType.JDBC_TABLE, List.of(), null, Map.of(), null));
        }
        CountDownLatch readsStarted = new CountDownLatch(2);
        AtomicInteger readCount = new AtomicInteger();
        when(adapter.read(any(), any())).thenAnswer(invocation -> {
            int read = readCount.incrementAndGet();
            readsStarted.countDown();
            assertTrue(readsStarted.await(2, TimeUnit.SECONDS), "independent input reads should overlap");
            String alias = invocation.<DatasetReadRequest>getArgument(1).inputName();
            return new DatasetBatch(List.of(Map.of("source", alias)), null, 1, true, null);
        });
        var service = new ScriptTaskPreparationService(datasets, new ScriptTaskInputRegistry(),
                new ScriptDatasetReadTokenService("secret"), List.of(adapter), mock(ObjectRefService.class));
        Map<String, Long> inputs = new LinkedHashMap<>();
        inputs.put("first", 3L);
        inputs.put("second", 4L);
        Map<String, DatasetQueryPlanDTO> plans = new LinkedHashMap<>();
        plans.put("first", emptyPlan(3L, "first"));
        plans.put("second", emptyPlan(4L, "second"));

        var prepared = service.prepare("parallel-task", 7L, 9L, inputs, "result = []", Map.of(), plans);

        assertEquals(List.of("first", "second"), new ArrayList<>(prepared.preparedInputs().keySet()));
        assertEquals("first", prepared.preparedInputs().get("first").inlineRows().getFirst().get("source"));
        assertEquals("second", prepared.preparedInputs().get("second").inlineRows().getFirst().get("source"));
        assertEquals(2, readCount.get());
    }

    @Test void validatesAllPlannedAliasesBeforeStartingAnySourceRead() {
        DatasetManageService datasets = mock(DatasetManageService.class);
        DatasetSourceAdapter adapter = mock(DatasetSourceAdapter.class);
        when(adapter.supports(DatasetSourceType.JDBC_TABLE)).thenReturn(true);
        when(datasets.getInputDescriptor(any(), eq(3L), eq("known"))).thenReturn(
                new DatasetInputDescriptor(3L, "known", DatasetSourceType.JDBC_TABLE, List.of(), null, Map.of(), null));
        var service = new ScriptTaskPreparationService(datasets, new ScriptTaskInputRegistry(),
                new ScriptDatasetReadTokenService("secret"), List.of(adapter), mock(ObjectRefService.class));
        Map<String, Long> inputs = Map.of("known", 3L);
        Map<String, DatasetQueryPlanDTO> plans = new LinkedHashMap<>();
        plans.put("known", emptyPlan(3L, "known"));
        plans.put("unknown", emptyPlan(4L, "unknown"));

        assertThrows(IllegalArgumentException.class,
                () -> service.prepare("invalid-plan", 7L, 9L, inputs, "result = []", Map.of(), plans));

        verify(adapter, after(500).never()).read(any(), any());
    }

    private DatasetQueryPlanDTO emptyPlan(long datasetId, String alias) {
        return new DatasetQueryPlanDTO(String.valueOf(datasetId), alias, List.of(), List.of(), List.of(), null,
                new DatasetQueryPlanDTO.PushdownSpec(false, false, false), List.of(), false, null);
    }

    @Test void materializesKnownStrategyReadoutFixtureWithoutJdbcAdapter() {
        long datasetId = StrategyReadoutDemoDatasetFixtures.DIMENSION_DATASET_ID;
        var fixtures = new StrategyReadoutDemoDatasetFixtures();
        DatasetManageService datasets = mock(DatasetManageService.class);
        when(datasets.getInputDescriptor(any(), eq(datasetId), eq("table_wd")))
                .thenAnswer(invocation -> fixtures.describe(invocation.getArgument(0), datasetId, "table_wd"));
        var registry = new ScriptTaskInputRegistry();
        var service = new ScriptTaskPreparationService(datasets, registry,
                new ScriptDatasetReadTokenService("secret"), List.of(), mock(ObjectRefService.class));
        service.setStrategyReadoutFixtures(fixtures);
        DatasetQueryPlanDTO plan = new DatasetQueryPlanDTO(String.valueOf(datasetId), "table_wd", List.of(),
                List.of(), List.of(), null, new DatasetQueryPlanDTO.PushdownSpec(false, false, false),
                List.of(), false, null);

        var prepared = service.prepare("fixture-task", 1L, 2L, Map.of("table_wd", datasetId),
                "result = []", Map.of(), Map.of("table_wd", plan));

        var input = prepared.preparedInputs().get("table_wd");
        assertNotNull(input);
        assertEquals(4, input.rowCount());
        assertTrue(input.schema().stream().anyMatch(column -> column.name().equals("metric_name")));
        assertEquals(4, registry.requirePreparedInput("fixture-task", "table_wd").inlineRows().size());
    }

    @Test void registersAliasesBeforeIssuingToken() {
        DatasetManageService datasets=mock(DatasetManageService.class); var tokens=new ScriptDatasetReadTokenService("secret");
        when(datasets.getInputDescriptor(any(),eq(3L),eq("orders"))).thenReturn(new DatasetInputDescriptor(3,"orders",DatasetSourceType.JDBC_TABLE,List.of(),null,Map.of(),null));
        var prepared=new ScriptTaskPreparationService(datasets,new ScriptTaskInputRegistry(),tokens,List.of(),mock(ObjectRefService.class))
                .prepare("task-1",7L,9L,Map.of("orders",3L),"print(1)",Map.of("region","east"));
        assertEquals("task-1",tokens.verify(prepared.readToken()).taskId());
        assertTrue(prepared.preparedInputs().isEmpty()); // 无查询计划时不物化 prepared input
    }

    @Test void materializesPreparedInputFromPlanAndCleansUpOnFailure() {
        DatasetManageService datasets=mock(DatasetManageService.class);
        DatasetSourceAdapter adapter=mock(DatasetSourceAdapter.class);
        when(adapter.supports(DatasetSourceType.JDBC_TABLE)).thenReturn(true);
        when(datasets.getInputDescriptor(any(),eq(3L),eq("orders"))).thenReturn(new DatasetInputDescriptor(3,"orders",DatasetSourceType.JDBC_TABLE,
                List.of(new DatasetColumn("in_account","入金","DECIMAL",true,"measure")),null,Map.of(),null));
        // 第 1 页返回 2 行（小于页大小 → 判定末页），不再翻页
        when(adapter.read(any(), any())).thenReturn(
                new DatasetBatch(List.of(Map.of("in_account",120),Map.of("in_account",80)),null,2,true,null));
        ObjectRefService refs=mock(ObjectRefService.class);
        var registry=new ScriptTaskInputRegistry();
        var service=new ScriptTaskPreparationService(datasets,registry,new ScriptDatasetReadTokenService("secret"),List.of(adapter),refs);
        DatasetQueryPlanDTO plan=new DatasetQueryPlanDTO("3","orders",List.of("in_account"),
                List.of(new DatasetQueryPlanDTO.FilterSpec("in_account","gte",50)),
                List.of(new DatasetQueryPlanDTO.OrderSpec("in_account","desc")),null,
                new DatasetQueryPlanDTO.PushdownSpec(true,true,false),List.of(),false,300,
                new vip.mate.dataagent.dto.QueryContextDTO.InputQueryOptions(true, "MONTH", null, null, 300,
                        null, Map.of("region", "east"), List.of()));
        var prepared=service.prepare("task-2",7L,9L,Map.of("orders",3L),"print(1)",Map.of(),Map.of("orders",plan));

        var input=prepared.preparedInputs().get("orders");
        assertNotNull(input);
        assertEquals(2,input.rowCount());
        assertEquals(2,input.inlineRows().size());
        assertNull(input.objectRef());
        assertEquals("in_account",registry.requirePreparedInput("task-2","orders").schema().getFirst().name());
        // 过滤条件进入 Adapter 读取请求（页面筛选已应用，Python 无需重读原始数据）
        var captured=org.mockito.ArgumentCaptor.forClass(DatasetReadRequest.class);
        verify(adapter,times(1)).read(any(),captured.capture());
        assertEquals("gte",captured.getAllValues().getFirst().filters().getFirst().operator());
        assertEquals(0,captured.getAllValues().getFirst().offset());
        assertEquals("MONTH", captured.getValue().timeGranularity());
        assertEquals(Map.of("region", "east"), captured.getValue().parameters());
        assertEquals(300, captured.getValue().limit());
    }

    @Test void shortCircuitPlanSkipsSourceAndPreparedInputExpiryIsExplainable() {
        DatasetManageService datasets=mock(DatasetManageService.class);
        DatasetSourceAdapter adapter=mock(DatasetSourceAdapter.class);
        when(datasets.getInputDescriptor(any(),eq(3L),eq("orders"))).thenReturn(new DatasetInputDescriptor(3,"orders",DatasetSourceType.JDBC_TABLE,List.of(),null,Map.of(),null));
        ObjectRefService refs=mock(ObjectRefService.class);
        var registry=new ScriptTaskInputRegistry();
        var service=new ScriptTaskPreparationService(datasets,registry,new ScriptDatasetReadTokenService("secret"),List.of(adapter),refs);
        DatasetQueryPlanDTO empty=new DatasetQueryPlanDTO("3","orders",List.of(),List.of(),List.of(),null,
                new DatasetQueryPlanDTO.PushdownSpec(false,false,false),List.of(),true,null);
        service.prepare("task-3",7L,9L,Map.of("orders",3L),"print(1)",Map.of(),Map.of("orders",empty));
        verify(adapter,never()).read(any(),any()); // 显式空集合不访问数据源
        assertEquals(0,registry.requirePreparedInput("task-3","orders").rowCount());

        // 过期：直接登记一条已过期的 prepared input 读取应得到可解释错误
        registry.registerPreparedInput("task-3",new ScriptTaskInputRegistry.PreparedInput("orders",
                List.of(),List.of(),null,0,System.currentTimeMillis()-1));
        var expired=assertThrows(ScriptTaskInputRegistry.PreparedInputExpiredException.class,
                ()->registry.requirePreparedInput("task-3","orders"));
        assertTrue(expired.getMessage().contains("expired"));
    }
}
