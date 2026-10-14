package vip.mate.dataagent.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vip.mate.dataagent.dto.InsightComponentAiAnalysisRequest;
import vip.mate.dataagent.dto.InsightDashboardVO;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.InsightDashboardService;
import vip.mate.dataagent.service.InsightDataBindService;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.repository.InsightReportMapper;
import vip.mate.dataagent.repository.InsightReportSubscriptionMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsightReportServiceImplComponentAnalysisTest {

    @Test
    void componentAnalysisPromptFocusesOnBusinessDataInsteadOfPageDiagnostics() {
        InsightDashboardService dashboardService = mock(InsightDashboardService.class);
        MateClawRuntime runtime = mock(MateClawRuntime.class);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setAgentId(7L);
        dashboard.setSchemaJson("{\"pages\":[{\"components\":[{\"id\":\"ai-1\",\"type\":\"aiAnalysis\"}]}]}");
        when(dashboardService.getDashboard(42L)).thenReturn(dashboard);
        when(runtime.chat(eq(7L), anyString(), anyString())).thenReturn("## 核心结论\n转化表现上升");

        InsightComponentAiAnalysisRequest request = new InsightComponentAiAnalysisRequest();
        request.setSnapshot(Map.of(
                "components", List.of(Map.of(
                        "title", "策略贡献表",
                        "type", "table",
                        "status", "success",
                        "table", Map.of("columns", List.of("策略", "转化用户数"), "rows", List.of(List.of("A", "12")))
                ))
        ));

        InsightReportServiceImpl service = new InsightReportServiceImpl(
                dashboardService,
                mock(InsightDataBindService.class),
                runtime,
                mock(WorkspaceGuard.class),
                mock(AloudataService.class),
                mock(InsightReportMapper.class),
                mock(InsightReportSubscriptionMapper.class));

        service.generateComponentAnalysis(42L, "ai-1", request);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(runtime).chat(eq(7L), prompt.capture(), anyString());
        assertThat(prompt.getValue())
                .contains("Business-data analysis only: focus on metric values and business dimensions; do not diagnose page or system technology.");
    }
}
