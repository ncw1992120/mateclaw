package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;
import vip.mate.dataagent.dataset.DatasetSourceType;
import vip.mate.dataagent.dto.DashboardExecutionRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.service.code.PythonExecutionService;
import vip.mate.dataagent.service.code.ScriptTaskPreparationService;
import vip.mate.dataagent.service.impl.DashboardExecutionServiceImpl;
import vip.mate.dataagent.repository.DashboardExecutionMapper;
import vip.mate.dataagent.objectref.ObjectRefService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardExecutionServiceTest {
    @Test
    void preparesSavedInputsAndSubmitsRunnerWithInternalReadEndpoint() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        ScriptTaskPreparationService preparation = mock(ScriptTaskPreparationService.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DashboardExecutionMapper executionMapper = mock(DashboardExecutionMapper.class);
        ObjectRefService objectRefs = mock(ObjectRefService.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        when(guard.currentUserId()).thenReturn(8L);

        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setId(42L);
        dashboard.setSchemaJson("{" +
                "\"version\":\"1.1\",\"script\":\"result = datasets.read(input_name='orders')\"," +
                "\"datasetInputs\":[{\"datasetId\":\"9\",\"inputName\":\"orders\"}]," +
                "\"executionPolicy\":{\"timeoutSeconds\":120}" +
                "}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        var descriptor = new DatasetInputDescriptor(9L, "orders", DatasetSourceType.JDBC_TABLE, List.of(), null, Map.of(), null);
        var prepared = new ScriptTaskPreparationService.PreparedTask(
                "dashboard-42-test", "result = datasets.read(input_name='orders')",
                Map.of("orders", descriptor), Map.of("date", "2026-09-12"), "secret-token");
        when(preparation.prepare(anyString(), eq(7L), eq(8L), eq(Map.of("orders", 9L)), anyString(), eq(Map.of("date", "2026-09-12"))))
                .thenReturn(prepared);
        when(runner.submit(anyMap())).thenReturn(Map.of("taskId", "runner-task", "status", "RUNNING"));

        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs, "http://mateclaw-dataagent:18089/dataagent/api/");
        Map<String, Object> response = service.submit(42L, new DashboardExecutionRequest(Map.of("date", "2026-09-12")));

        assertEquals(42L, response.get("dashboardId"));
        assertEquals("RUNNING", response.get("status"));
        String executionId = (String) response.get("executionId");
        assertTrue(executionId.startsWith("dashboard-42-"));

        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runner).submit(captor.capture());
        Map<?, ?> runnerRequest = captor.getValue();
        assertEquals("http://mateclaw-dataagent:18089/dataagent/api/internal/v1/script-tasks/" + executionId + "/datasets/read", runnerRequest.get("datasetReadEndpoint"));
        assertEquals("secret-token", runnerRequest.get("readToken"));
        assertFalse(runnerRequest.containsKey("requirements"));
    }

    @Test
    void rejectsUnknownExecutionBeforeCallingRunner() {
        var service = new DashboardExecutionServiceImpl(
                mock(InsightDashboardService.class), mock(ScriptTaskPreparationService.class),
                mock(PythonExecutionService.class), mock(WorkspaceGuard.class), new ObjectMapper(), mock(DashboardExecutionMapper.class), mock(ObjectRefService.class),
                "http://mateclaw-server:18088");
        assertThrows(IllegalArgumentException.class, () -> service.status("dashboard-42-unknown"));
    }

    @Test
    void rejectsExecutionParameterNotDeclaredBySchemaBeforePreparingRunnerTask() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        ScriptTaskPreparationService preparation = mock(ScriptTaskPreparationService.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DashboardExecutionMapper executionMapper = mock(DashboardExecutionMapper.class);
        ObjectRefService objectRefs = mock(ObjectRefService.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        when(guard.currentUserId()).thenReturn(8L);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setId(42L);
        dashboard.setSchemaJson("{" +
                "\"script\":\"result = []\",\"datasetInputs\":[{" +
                "\"datasetId\":9,\"inputName\":\"orders\"}]," +
                "\"parameters\":[{\"name\":\"region\",\"type\":\"string\",\"scope\":\"dashboard\"}]}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs, "http://mateclaw-server:18088");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submit(42L, new DashboardExecutionRequest(Map.of("unknown", "value"))));

        assertEquals("unknown dashboard execution parameter: unknown", error.getMessage());
        verifyNoInteractions(preparation, runner);
    }

    @Test
    void acceptsDashboardTimeRangeObjectForDateRangeScriptParameter() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        ScriptTaskPreparationService preparation = mock(ScriptTaskPreparationService.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DashboardExecutionMapper executionMapper = mock(DashboardExecutionMapper.class);
        ObjectRefService objectRefs = mock(ObjectRefService.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        when(guard.currentUserId()).thenReturn(8L);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setId(42L);
        dashboard.setSchemaJson("{\"script\":\"result=[]\",\"datasetInputs\":[{\"datasetId\":9,\"inputName\":\"orders\"}],"
                + "\"parameters\":[{\"name\":\"window\",\"type\":\"date_range\",\"scope\":\"dashboard\"}]}" );
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        var prepared = new ScriptTaskPreparationService.PreparedTask("task", "result=[]", Map.of(), Map.of(), "token");
        when(preparation.prepare(anyString(), eq(7L), eq(8L), anyMap(), anyString(),
                eq(Map.of("window", Map.of("preset", "7d"))))).thenReturn(prepared);
        when(runner.submit(anyMap())).thenReturn(Map.of("status", "RUNNING"));

        new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs,
                "http://mateclaw-server:18088").submit(42L,
                new DashboardExecutionRequest(Map.of("window", Map.of("preset", "7d"))));

        verify(preparation).prepare(anyString(), eq(7L), eq(8L), anyMap(), anyString(),
                eq(Map.of("window", Map.of("preset", "7d"))));
    }
}
