package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.DatasetInputDescriptor;
import vip.mate.dataagent.dataset.DatasetSourceType;
import vip.mate.dataagent.dto.DashboardExecutionRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.dto.DatasetQueryPlanDTO;
import vip.mate.dataagent.dto.QueryContextDTO;
import vip.mate.dataagent.service.code.PythonExecutionService;
import vip.mate.dataagent.service.code.ScriptTaskPreparationService;
import vip.mate.dataagent.service.impl.DashboardExecutionServiceImpl;
import vip.mate.dataagent.service.impl.QueryPlannerImpl;
import vip.mate.dataagent.repository.DashboardExecutionMapper;
import vip.mate.dataagent.objectref.ObjectRefService;
import vip.mate.dataagent.model.DashboardExecutionEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardExecutionServiceTest {
    @Test
    void resolvesScopedDatasetIdBeforePlanningAndPreparingPythonInputs() throws Exception {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        ScriptTaskPreparationService preparation = mock(ScriptTaskPreparationService.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        var datasets = mock(vip.mate.dataagent.repository.DatasetMapper.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        when(guard.currentUserId()).thenReturn(8L);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setSchemaJson("{}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        var current = new vip.mate.dataagent.model.DatasetEntity(); current.setId(99L);
        when(datasets.selectOne(any())).thenReturn(current);
        when(preparation.prepare(anyString(), eq(7L), eq(8L), anyMap(), eq("result=[]"), anyMap(), anyMap(), eq(true)))
                .thenReturn(new ScriptTaskPreparationService.PreparedTask("task", "result=[]", Map.of(), Map.of(), "token"));
        when(runner.submit(anyMap())).thenReturn(Map.of("taskId", "task", "status", "RUNNING"));
        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard,
                new ObjectMapper(), mock(DashboardExecutionMapper.class), mock(ObjectRefService.class),
                new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://localhost:18089", new QueryPlannerImpl());
        service.setComponentDatasetResolver(new vip.mate.dataagent.service.impl.ComponentDatasetResolver(datasets, guard));
        String schema = """
                {"pages":[{"components":[{"id":"card-1","config":{"datasetPipeline":{
                  "datasetInputs":[{"datasetId":"9","inputName":"table_ab1"}],"script":"result=[]"
                }}}]}]}
                """;
        service.submit(42L, new DashboardExecutionRequest(Map.of(), "card-1", schema,
                new QueryContextDTO("42", "card-1", null, Map.of(), null, null, "scope-test")));

        @SuppressWarnings("unchecked")
        var plans = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(preparation).prepare(anyString(), eq(7L), eq(8L), eq(Map.of("table_ab1", 99L)),
                eq("result=[]"), anyMap(), plans.capture(), eq(true));
        assertEquals("99", ((DatasetQueryPlanDTO) plans.getValue().get("table_ab1")).datasetId());
    }

    @Test
    void structuredDataFailureKeepsUiDetailsButOmitsValuesFromServiceLogs() {
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DashboardExecutionMapper executionMapper = mock(DashboardExecutionMapper.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        DashboardExecutionEntity execution = new DashboardExecutionEntity();
        execution.setExecutionId("diagnostic-failed");
        execution.setWorkspaceId(7L);
        execution.setDashboardId(42L);
        execution.setStatus("RUNNING");
        when(executionMapper.selectOne(any())).thenReturn(execution);
        String error = """
                {"code":"DATA_VALIDATION_ERROR","message":"数据校验失败",
                 "errorDetails":{"dataset":"table_zb","field":"amount","scriptLine":3,"errorCount":1,
                 "issues":[{"field":"users","rowNumber":128,"rowKeys":{"id":"secret-id"},"valuePreview":"secret-value","reason":"无法转换为数字"}]},
                 "traceback":"secret-script"}
                """;
        when(runner.getStatus("diagnostic-failed")).thenReturn(Map.of("status", "FAILED", "error", error));
        Logger logger = (Logger) LoggerFactory.getLogger(DashboardExecutionServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var service = new DashboardExecutionServiceImpl(mock(InsightDashboardService.class),
                    mock(ScriptTaskPreparationService.class), runner, guard, new ObjectMapper(), executionMapper,
                    mock(ObjectRefService.class), new vip.mate.dataagent.service.code.ScriptResultContractService(),
                    "http://mateclaw-dataagent:18089/dataagent/api/");
            assertEquals(error, service.status("diagnostic-failed").get("error"));
            String log = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("stage=python-execution-failed")).findFirst().orElseThrow();
            assertTrue(log.contains("scriptLine=3"));
            assertTrue(log.contains("field=amount"));
            assertTrue(log.contains("row=128"));
            assertTrue(log.contains("issueField=users"));
            assertFalse(log.contains("secret-value"));
            assertFalse(log.contains("secret-id"));
            assertFalse(log.contains("secret-script"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsPythonFailureDetailsWithoutLoggingScriptOrParameters() {
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DashboardExecutionMapper executionMapper = mock(DashboardExecutionMapper.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        DashboardExecutionEntity execution = new DashboardExecutionEntity();
        execution.setExecutionId("dashboard-42-failed");
        execution.setWorkspaceId(7L);
        execution.setDashboardId(42L);
        execution.setStatus("RUNNING");
        when(executionMapper.selectOne(any())).thenReturn(execution);
        when(runner.getStatus("dashboard-42-failed")).thenReturn(Map.of(
                "status", "FAILED",
                "error", "Traceback (most recent call last):\n  File \"user_script.py\", line 1\n    print('secret script')\nValueError: metric_id column is missing\n\nit might also be that a value overflows the data-type's capacity",
                "returncode", 1,
                "script", "secret script",
                "parameters", Map.of("token", "secret parameter")));

        Logger logger = (Logger) LoggerFactory.getLogger(DashboardExecutionServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var service = new DashboardExecutionServiceImpl(
                    mock(InsightDashboardService.class), mock(ScriptTaskPreparationService.class), runner,
                    guard, new ObjectMapper(), executionMapper, mock(ObjectRefService.class),
                    new vip.mate.dataagent.service.code.ScriptResultContractService(),
                    "http://mateclaw-dataagent:18089/dataagent/api/");

            Map<String, Object> response = service.status("dashboard-42-failed");

            assertTrue(((String) response.get("error")).contains("File \"user_script.py\", line 1"));
            assertTrue(((String) response.get("error")).contains("ValueError: metric_id column is missing"));
            assertEquals(1, response.get("returnCode"));
            String log = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains("stage=python-execution-failed"))
                    .findFirst().orElseThrow();
            assertTrue(log.contains("dashboardId=42"));
            assertTrue(log.contains("executionId=dashboard-42-failed"));
            assertTrue(log.contains("status=FAILED"));
            assertTrue(log.contains("returnCode=1"));
            assertTrue(log.contains("scriptLine=1"));
            assertTrue(log.contains("ValueError: metric_id column is missing"));
            assertFalse(log.contains("user_script.py"));
            assertFalse(log.contains("secret script"));
            assertFalse(log.contains("secret parameter"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void structuredPythonErrorLogsLocationButNotSourceCode() throws Exception {
        var service = new DashboardExecutionServiceImpl(mock(InsightDashboardService.class),
                mock(ScriptTaskPreparationService.class), mock(PythonExecutionService.class),
                mock(WorkspaceGuard.class), new ObjectMapper(), mock(DashboardExecutionMapper.class),
                mock(ObjectRefService.class), new vip.mate.dataagent.service.code.ScriptResultContractService(),
                "http://mateclaw-dataagent:18089/dataagent/api/");
        var method = DashboardExecutionServiceImpl.class.getDeclaredMethod("pythonFailureLogDetails", String.class);
        method.setAccessible(true);
        String error = """
                {"code":"PYTHON_EXECUTION_ERROR","exceptionType":"polars.exceptions.ComputeError",
                 "message":"could not append value\\n\\nit might also be that a value overflows capacity",
                 "errorContext":{"scriptLine":19,"region":"system","inputName":"table_zb","conversion":"Polars",
                 "sourceLines":[{"line":19,"code":"secret-script"}]},"traceback":"secret-traceback"}
                """;
        String detail = method.invoke(service, error).toString();
        assertTrue(detail.contains("scriptLine=19"));
        assertTrue(detail.contains("inputName=table_zb"));
        assertTrue(detail.contains("region=system"));
        assertTrue(detail.contains("polars.exceptions.ComputeError: could not append value"));
        assertFalse(detail.contains("secret-script"));
        assertFalse(detail.contains("secret-traceback"));
    }

    @Test
    void executesNestedTabComponentWithIndependentPerInputFilterPlans() throws Exception {
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
        dashboard.setSchemaJson("{}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        when(preparation.prepare(anyString(), eq(7L), eq(8L), anyMap(), eq("result = []"), anyMap(), anyMap(), eq(true)))
                .thenReturn(new ScriptTaskPreparationService.PreparedTask("task", "result = []", Map.of(), Map.of(), "token"));
        when(runner.submit(anyMap())).thenReturn(Map.of("status", "RUNNING"));

        String schemaJson = """
                {
                  "pages": [{"id": "main", "components": [{
                    "id": "combination", "type": "combination",
                    "containerConfig": {"tabs": [{"id": "tab-1", "children": [{
                      "id": "nested-python", "type": "table",
                      "config": {"datasetPipeline": {
                        "script": "result = []",
                        "boundFilterComponentIds": ["date-filter", "region-filter"],
                        "datasetInputs": [
                          {"datasetId": 9, "inputName": "input_a", "queryConfig": {
                            "displayFields": [{"field": "event_date_a", "title": "日期"}],
                            "queryableFields": [{"name": "event_date_a", "role": "dimension"}, {"name": "region_a", "role": "dimension"}],
                            "parameterBindings": [
                              {"filterComponentId": "date-filter", "parameterName": "date_from_a", "field": "event_date_a", "operator": "gte"},
                              {"filterComponentId": "region-filter", "parameterName": "regions_a", "field": "region_a", "operator": "in"}
                            ]
                          }},
                          {"datasetId": 10, "inputName": "input_b", "queryConfig": {
                            "displayFields": [{"field": "event_date_b", "title": "日期"}],
                            "queryableFields": [{"name": "event_date_b", "role": "dimension"}, {"name": "region_b", "role": "dimension"}],
                            "parameterBindings": [
                              {"filterComponentId": "date-filter", "parameterName": "date_from_b", "field": "event_date_b", "operator": "gte"},
                              {"filterComponentId": "region-filter", "parameterName": "regions_b", "field": "region_b", "operator": "in"}
                            ]
                          }},
                          {"datasetId": 11, "inputName": "input_unbound", "queryConfig": {
                            "displayFields": [{"field": "unfiltered_value", "title": "未绑定"}],
                            "queryableFields": [{"name": "unfiltered_value", "role": "dimension"}],
                            "parameterBindings": []
                          }}
                        ]
                      }}
                    }]}]}
                  }] }]
                }
                """;
        Map<String, Object> runtimeParameters = Map.of(
                "date_from_a", "2026-09-01", "date_from_b", "2026-09-01",
                "regions_a", List.of("north", "south"), "regions_b", List.of("north", "south"));
        QueryContextDTO queryContext = new QueryContextDTO(
                "dashboard-42", "nested-python", null, runtimeParameters, null, null, "request-123");
        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(),
                executionMapper, objectRefs, new vip.mate.dataagent.service.code.ScriptResultContractService(),
                "http://mateclaw-dataagent:18089/dataagent/api/", new QueryPlannerImpl());

        service.submit(42L, new DashboardExecutionRequest(Map.of(), "nested-python", schemaJson, queryContext));

        var plansCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(preparation).prepare(anyString(), eq(7L), eq(8L), anyMap(), eq("result = []"), anyMap(), plansCaptor.capture(), eq(true));
        @SuppressWarnings("unchecked")
        Map<String, DatasetQueryPlanDTO> plans = plansCaptor.getValue();
        assertEquals(List.of("event_date_a", "region_a"), plans.get("input_a").filters().stream()
                .map(DatasetQueryPlanDTO.FilterSpec::field).toList());
        assertEquals(List.of("event_date_b", "region_b"), plans.get("input_b").filters().stream()
                .map(DatasetQueryPlanDTO.FilterSpec::field).toList());
        assertTrue(plans.get("input_a").filters().stream().anyMatch(filter ->
                filter.field().equals("region_a") && filter.value().equals(List.of("north", "south"))));
        assertTrue(plans.get("input_unbound").filters().isEmpty());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runner).submit(requestCaptor.capture());
        assertEquals(Map.of(), requestCaptor.getValue().get("parameters"));
        assertEquals(Boolean.TRUE, requestCaptor.getValue().get("preferPreparedInputs"));
    }

    @Test
    void executesTransientPreviewSchemaWithoutReloadingOrSavingDashboardSchema() {
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
        dashboard.setSchemaJson("{\"script\":\"old\",\"datasetInputs\":[]}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        when(preparation.prepare(anyString(), eq(7L), eq(8L), eq(Map.of("orders", 9L)), eq("result=[]"), anyMap()))
                .thenReturn(new ScriptTaskPreparationService.PreparedTask("task", "result=[]", Map.of("orders", new DatasetInputDescriptor(9L, "orders", DatasetSourceType.JDBC_TABLE, List.of(), null, Map.of(), null)), Map.of(), "token"));
        when(runner.submit(anyMap())).thenReturn(Map.of("status", "RUNNING"));

        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs,
                new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-dataagent:18089/dataagent/api/");
        service.submit(42L, new DashboardExecutionRequest(Map.of(), "card-1",
                "{\"pages\":[{\"components\":[{\"id\":\"card-1\",\"config\":{\"datasetPipeline\":{\"script\":\"result=[]\",\"datasetInputs\":[{\"datasetId\":9,\"inputName\":\"orders\"}]}}}]}]}"));

        verify(dashboards).getDashboard(42L);
        verifyNoMoreInteractions(dashboards);
        verify(runner).submit(anyMap());
    }

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

        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs, new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-dataagent:18089/dataagent/api/");
        Map<String, Object> response = service.submit(42L, new DashboardExecutionRequest(Map.of("date", "2026-09-12")));

        assertEquals(42L, response.get("dashboardId"));
        assertEquals("RUNNING", response.get("status"));
        String executionId = (String) response.get("executionId");
        assertTrue(executionId.startsWith("dashboard-42-"));

        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runner).submit(captor.capture());
        Map<?, ?> runnerRequest = captor.getValue();
        Map<?, ?> limits = (Map<?, ?>) runnerRequest.get("limits");
        assertEquals(120, limits.get("timeout_seconds"));
        assertEquals(50000, limits.get("max_stdout_bytes"));
        assertEquals("result = datasets.read(input_name='orders')", runnerRequest.get("script"));
        assertEquals(Map.of("orders", descriptor), runnerRequest.get("inputCatalog"));
        assertEquals(Map.of("date", "2026-09-12"), runnerRequest.get("parameters"));
        assertEquals("http://mateclaw-dataagent:18089/dataagent/api/internal/v1/script-tasks/" + executionId + "/datasets/read", runnerRequest.get("datasetReadEndpoint"));
        assertEquals("http://mateclaw-dataagent:18089/dataagent/api/internal/v1/script-tasks/" + executionId + "/datasets/input", runnerRequest.get("datasetInputEndpoint"));
        assertEquals("http://mateclaw-dataagent:18089/dataagent/api/internal/v1/script-tasks/" + executionId + "/result", runnerRequest.get("resultUploadEndpoint"));
        assertEquals("secret-token", runnerRequest.get("readToken"));
        assertEquals(Boolean.FALSE, runnerRequest.get("preferPreparedInputs"));
        assertFalse(runnerRequest.containsKey("requirements"));
    }

    @Test
    void usesComponentPipelineExecutionPolicyWhenRootPolicyIsEmpty() {
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
        dashboard.setSchemaJson("{\"executionPolicy\":{},\"pages\":[{\"components\":[{\"id\":\"card-1\",\"type\":\"table\",\"config\":{\"datasetPipeline\":{\"datasetInputs\":[{\"datasetId\":9,\"inputName\":\"orders\"}],\"script\":\"result=[]\",\"executionPolicy\":{\"timeoutSeconds\":3}}}}]}]}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        when(preparation.prepare(anyString(), eq(7L), eq(8L), anyMap(), anyString(), anyMap()))
                .thenReturn(new ScriptTaskPreparationService.PreparedTask("task", "result=[]", Map.of(), Map.of(), "token"));
        when(runner.submit(anyMap())).thenReturn(Map.of("status", "RUNNING"));

        new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs,
                new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-server:18088")
                .submit(42L, new DashboardExecutionRequest(Map.of(), "card-1"));

        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runner).submit(captor.capture());
        assertEquals(3, ((Map<?, ?>) captor.getValue().get("limits")).get("timeout_seconds"));
    }

    @Test
    void rejectsUnknownExecutionBeforeCallingRunner() {
        var service = new DashboardExecutionServiceImpl(
                mock(InsightDashboardService.class), mock(ScriptTaskPreparationService.class),
                mock(PythonExecutionService.class), mock(WorkspaceGuard.class), new ObjectMapper(), mock(DashboardExecutionMapper.class), mock(ObjectRefService.class),
                new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-server:18088");
        assertThrows(IllegalArgumentException.class, () -> service.status("dashboard-42-unknown"));
    }

    @Test
    void skipsDatabaseWriteWhenPolledRunnerSnapshotHasNotChanged() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        ScriptTaskPreparationService preparation = mock(ScriptTaskPreparationService.class);
        PythonExecutionService runner = mock(PythonExecutionService.class);
        WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DashboardExecutionMapper executionMapper = mock(DashboardExecutionMapper.class);
        when(guard.currentWorkspaceId()).thenReturn(7L);
        DashboardExecutionEntity execution = new DashboardExecutionEntity();
        execution.setExecutionId("dashboard-42-task");
        execution.setWorkspaceId(7L);
        execution.setDashboardId(42L);
        execution.setStatus("RUNNING");
        execution.setLogs("progress");
        execution.setOutputRefJson("{}");
        when(executionMapper.selectOne(any())).thenReturn(execution);
        when(runner.getStatus("dashboard-42-task")).thenReturn(
                Map.of("status", "RUNNING", "output", "progress", "outputRef", Map.of()),
                Map.of("status", "RUNNING", "output", "progress\nmore", "outputRef", Map.of()));
        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(),
                executionMapper, mock(ObjectRefService.class), new vip.mate.dataagent.service.code.ScriptResultContractService(),
                "http://mateclaw-dataagent:18089/dataagent/api/");

        service.status("dashboard-42-task");
        service.status("dashboard-42-task");

        verify(executionMapper, times(1)).updateById(execution);
        assertEquals("progress\nmore", execution.getLogs());
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
        var service = new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs, new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-server:18088");

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
                new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-server:18088").submit(42L,
                new DashboardExecutionRequest(Map.of("window", Map.of("preset", "7d"))));

        verify(preparation).prepare(anyString(), eq(7L), eq(8L), anyMap(), anyString(),
                eq(Map.of("window", Map.of("preset", "7d"))));
    }

    @Test
    void omitsUnsetOptionalBoundParameterInsteadOfPassingAnEmptyValue() {
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
                + "\"parameters\":[{\"name\":\"startDate\",\"type\":\"date\",\"scope\":\"dashboard\"},"
                + "{\"name\":\"endDate\",\"type\":\"date\",\"scope\":\"dashboard\"}]}" );
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        var prepared = new ScriptTaskPreparationService.PreparedTask("task", "result=[]", Map.of(), Map.of("startDate", "2026-09-01"), "token");
        when(preparation.prepare(anyString(), eq(7L), eq(8L), anyMap(), anyString(),
                eq(Map.of("startDate", "2026-09-01")))).thenReturn(prepared);
        when(runner.submit(anyMap())).thenReturn(Map.of("status", "RUNNING"));

        new DashboardExecutionServiceImpl(dashboards, preparation, runner, guard, new ObjectMapper(), executionMapper, objectRefs,
                new vip.mate.dataagent.service.code.ScriptResultContractService(), "http://mateclaw-server:18088").submit(42L,
                new DashboardExecutionRequest(Map.of("startDate", "2026-09-01")));

        verify(preparation).prepare(anyString(), eq(7L), eq(8L), anyMap(), anyString(),
                eq(Map.of("startDate", "2026-09-01")));
    }
}
