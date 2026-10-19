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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;
import vip.mate.dataagent.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsightReportServiceImplComponentAnalysisTest {

    @Test
    void cancellingActiveAnalysisCancelsModelSubscriptionAndDoesNotPersist() throws Exception {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        MateClawRuntime runtime = mock(MateClawRuntime.class);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setAgentId(7L);
        dashboard.setSchemaJson("{\"pages\":[{\"components\":[{\"id\":\"ai-1\",\"type\":\"aiAnalysis\"}]}]}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        CountDownLatch subscribed = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        when(runtime.chatStructuredStream(eq(7L), anyString(), anyString())).thenReturn(
                Flux.<vip.mate.agent.AgentService.StreamDelta>never()
                        .doOnSubscribe(subscription -> subscribed.countDown())
                        .doOnCancel(() -> cancelled.set(true)));
        InsightReportServiceImpl service = new InsightReportServiceImpl(dashboards, mock(InsightDataBindService.class),
                runtime, mock(WorkspaceGuard.class), mock(AloudataService.class), mock(InsightReportMapper.class),
                mock(InsightReportSubscriptionMapper.class));
        InsightComponentAiAnalysisRequest request = new InsightComponentAiAnalysisRequest();
        request.setRequestId("analysis-1");
        request.setSnapshot(Map.of("components", List.of()));
        CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> service.generateComponentAnalysis(42L, "ai-1", request));
        assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
        service.cancelComponentAnalysis(42L, "ai-1", "analysis-1");
        assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
        assertThat(cancelled).isTrue();
        verify(dashboards, never()).updateDashboard(any(), any());
    }

    @Test
    void cancellationArrivingBeforeAnalysisPreventsModelExecution() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        MateClawRuntime runtime = mock(MateClawRuntime.class);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setAgentId(7L);
        dashboard.setSchemaJson("{\"pages\":[{\"components\":[{\"id\":\"ai-1\",\"type\":\"aiAnalysis\"}]}]}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        InsightReportServiceImpl service = new InsightReportServiceImpl(dashboards, mock(InsightDataBindService.class),
                runtime, mock(WorkspaceGuard.class), mock(AloudataService.class), mock(InsightReportMapper.class),
                mock(InsightReportSubscriptionMapper.class));
        service.cancelComponentAnalysis(42L, "ai-1", "analysis-1");
        InsightComponentAiAnalysisRequest request = new InsightComponentAiAnalysisRequest();
        request.setRequestId("analysis-1");
        request.setSnapshot(Map.of("components", List.of()));
        assertThatThrownBy(() -> service.generateComponentAnalysis(42L, "ai-1", request)).isInstanceOf(BusinessException.class);
        verify(runtime, never()).chatStructuredStream(any(), anyString(), anyString());
        verify(dashboards, never()).updateDashboard(any(), any());
    }

    @Test
    void cancellableAnalysisPersistsOnlyCompleteModelContent() {
        InsightDashboardService dashboards = mock(InsightDashboardService.class);
        MateClawRuntime runtime = mock(MateClawRuntime.class);
        InsightDashboardVO dashboard = new InsightDashboardVO();
        dashboard.setAgentId(7L);
        dashboard.setSchemaJson("{\"pages\":[{\"components\":[{\"id\":\"ai-1\",\"type\":\"aiAnalysis\"}]}]}");
        when(dashboards.getDashboard(42L)).thenReturn(dashboard);
        when(runtime.chatStructuredStream(eq(7L), anyString(), anyString())).thenReturn(Flux.just(
                new vip.mate.agent.AgentService.StreamDelta("核心结论：", null),
                vip.mate.agent.AgentService.StreamDelta.event("status", Map.of()),
                new vip.mate.agent.AgentService.StreamDelta("转化表现上升", null)));
        InsightReportServiceImpl service = new InsightReportServiceImpl(dashboards, mock(InsightDataBindService.class),
                runtime, mock(WorkspaceGuard.class), mock(AloudataService.class), mock(InsightReportMapper.class),
                mock(InsightReportSubscriptionMapper.class));
        InsightComponentAiAnalysisRequest request = new InsightComponentAiAnalysisRequest();
        request.setRequestId("analysis-1");
        request.setSnapshot(Map.of("components", List.of()));
        assertThat(service.generateComponentAnalysis(42L, "ai-1", request)).isEqualTo("核心结论：转化表现上升");
        var update = ArgumentCaptor.forClass(vip.mate.dataagent.dto.InsightDashboardUpdateRequest.class);
        verify(dashboards).updateDashboard(eq(42L), update.capture());
        assertThat(update.getValue().getSchemaJson()).contains("核心结论：转化表现上升");
    }

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
