package vip.mate.dataagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.controller.DataAgentInsightDashboardController;
import vip.mate.dataagent.service.DashboardExecutionService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.InsightDataBindService;
import vip.mate.dataagent.service.InsightReportService;
import vip.mate.dataagent.service.ResultSetQueryService;
import vip.mate.dataagent.service.QueryPlanErrorCodes;
import vip.mate.dataagent.service.QueryPlanException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 组件执行 QueryContext 契约与结果集预览端点（实施计划任务 7）。 */
class DataAgentInsightDashboardControllerQueryTest {

    private DashboardExecutionService executionService;
    private ResultSetQueryService resultSetQueryService;
    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        InsightDataBindService bind = mock(InsightDataBindService.class);
        InsightReportService reports = mock(InsightReportService.class);
        executionService = mock(DashboardExecutionService.class);
        resultSetQueryService = mock(ResultSetQueryService.class);
        DataAgentInsightDashboardController controller = new DataAgentInsightDashboardController(
                dashboards, bind, reports, executionService, resultSetQueryService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new vip.mate.dataagent.exception.DataAgentGlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("组件执行透传 queryContext 到执行服务（存量 parameters 不回归）")
    void componentExecutionPassesQueryContext() throws Exception {
        when(executionService.submit(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(Map.of("executionId", "e1", "status", "RUNNING"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parameters", Map.of("legacy_param", "v"));
        body.put("queryContext", Map.of(
                "dashboardId", "1", "componentId", "component-001",
                "parameters", Map.of("strategy_ids", List.of("A", "B")),
                "sort", Map.of("field", "in_account", "direction", "desc"),
                "pagination", Map.of("page", 2, "pageSize", 100),
                "requestId", "run-001"));
        mvc.perform(post("/v1/insight/dashboards/1/components/component-001/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionId").value("e1"));
        var captor = org.mockito.ArgumentCaptor.forClass(vip.mate.dataagent.dto.DashboardExecutionRequest.class);
        verify(executionService).submit(eq(1L), captor.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().queryContext());
        org.junit.jupiter.api.Assertions.assertEquals("run-001", captor.getValue().queryContext().requestId());
        // 存量 parameters 继续原样传递
        org.junit.jupiter.api.Assertions.assertEquals("v", captor.getValue().parameters().get("legacy_param"));
    }

    @Test
    @DisplayName("结果集预览端点返回 columns/rows/totalCount/page/pageSize")
    void resultPreviewEndpoint() throws Exception {
        when(resultSetQueryService.preview(eq("e1"), any())).thenReturn(Map.of(
                "columns", List.of("v"), "rows", List.of(Map.of("v", 1)),
                "totalCount", 25, "page", 2, "pageSize", 10));
        mvc.perform(post("/v1/insight/dashboards/executions/e1/result/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("pagination", Map.of("page", 2, "pageSize", 10)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(25))
                .andExpect(jsonPath("$.data.page").value(2));
    }

    @Test
    @DisplayName("结果未就绪映射 RESULT_NOT_READY；错误不透出堆栈")
    void resultPreviewErrorMapping() throws Exception {
        when(resultSetQueryService.preview(eq("e2"), any()))
                .thenThrow(QueryPlanException.of(QueryPlanErrorCodes.RESULT_NOT_READY, "execution is still running: RUNNING"));
        mvc.perform(post("/v1/insight/dashboards/executions/e2/result/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()) // 全局处理器：QueryPlanException → 400 稳定信封
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("RESULT_NOT_READY")));
    }
}
