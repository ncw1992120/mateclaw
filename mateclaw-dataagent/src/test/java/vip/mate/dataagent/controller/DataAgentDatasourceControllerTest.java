package vip.mate.dataagent.controller;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.auth.annotation.RequireGlobalAdmin;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewSummary;
import vip.mate.dataagent.service.AloudataAnalysisViewService;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DataAgentDatasourceControllerTest {
    @Test
    void exposesReadOnlyAnalysisViewEndpointsThroughService() {
        DatasourceManageService datasourceService = mock(DatasourceManageService.class);
        AloudataEndpointService endpointService = mock(AloudataEndpointService.class);
        AloudataSemanticSyncService syncService = mock(AloudataSemanticSyncService.class);
        AloudataService aloudataService = mock(AloudataService.class);
        AloudataAnalysisViewService viewService = mock(AloudataAnalysisViewService.class);
        WorkspaceGuard workspaceGuard = mock(WorkspaceGuard.class);
        DataAgentDatasourceController controller = new DataAgentDatasourceController(
                datasourceService, endpointService, syncService, aloudataService, viewService, workspaceGuard);
        when(viewService.listTree(4L)).thenReturn(List.of(
                new AloudataAnalysisViewSummary("v1", "sales", "销售", "c1", "经营")));
        AloudataAnalysisViewDetail detail = new AloudataAnalysisViewDetail(
                "v1", "sales", "销售", null, List.of(), List.of(), null, List.of(), List.of(), List.of());
        when(viewService.getByName(4L, "sales")).thenReturn(detail);

        assertEquals(1, controller.listAnalysisViews(4L).getData().size());
        assertEquals("sales", controller.getAnalysisView(4L, "sales").getData().viewName());
        verify(viewService).listTree(4L);
        verify(viewService).getByName(4L, "sales");
    }

    @Test
    void everyDatasourceEndpointHasWorkspaceOrGlobalAdminBoundary() {
        for (java.lang.reflect.Method method : DataAgentDatasourceController.class.getDeclaredMethods()) {
            if (!vip.mate.common.result.R.class.isAssignableFrom(method.getReturnType())) continue;
            assertTrue(method.isAnnotationPresent(RequireWorkspaceRole.class)
                    || method.isAnnotationPresent(RequireGlobalAdmin.class),
                    () -> "missing security boundary on " + method.getName());
        }
    }
}
