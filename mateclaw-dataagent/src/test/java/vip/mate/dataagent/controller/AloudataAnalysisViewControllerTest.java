package vip.mate.dataagent.controller;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewSummary;
import vip.mate.dataagent.service.AloudataAnalysisViewService;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Controller-level contract checks for the read-only Aloudata analysis-view API. */
class AloudataAnalysisViewControllerTest {

    @Test
    void listAndDetailDelegateToReadOnlyService() {
        AloudataAnalysisViewService viewService = mock(AloudataAnalysisViewService.class);
        DataAgentDatasourceController controller = controller(viewService);
        when(viewService.listTree(17L)).thenReturn(List.of(
                new AloudataAnalysisViewSummary("id-1", "sales", "销售", "cat-1", "经营")));
        when(viewService.getByName(17L, "sales")).thenReturn(new AloudataAnalysisViewDetail(
                "id-1", "sales", "销售", "仅只读", List.of(), List.of(), null,
                List.of(), List.of(), List.of()));

        var tree = controller.listAnalysisViews(17L);
        var detail = controller.getAnalysisView(17L, "sales");

        assertNotNull(tree.getData());
        assertEquals("sales", tree.getData().get(0).viewName());
        assertEquals("sales", detail.getData().viewName());
        assertEquals("仅只读", detail.getData().description());
        verify(viewService).listTree(17L);
        verify(viewService).getByName(17L, "sales");
    }

    @Test
    void exposesOnlyReadOnlyWorkspaceBoundEndpoints() throws NoSuchMethodException {
        var list = DataAgentDatasourceController.class
                .getDeclaredMethod("listAnalysisViews", Long.class);
        var detail = DataAgentDatasourceController.class
                .getDeclaredMethod("getAnalysisView", Long.class, String.class);

        assertTrue(list.isAnnotationPresent(vip.mate.dataagent.auth.annotation.RequireWorkspaceRole.class));
        assertTrue(detail.isAnnotationPresent(vip.mate.dataagent.auth.annotation.RequireWorkspaceRole.class));
    }

    private DataAgentDatasourceController controller(AloudataAnalysisViewService viewService) {
        return new DataAgentDatasourceController(
                mock(DatasourceManageService.class),
                mock(AloudataEndpointService.class),
                mock(AloudataSemanticSyncService.class),
                mock(AloudataService.class),
                viewService,
                mock(WorkspaceGuard.class));
    }
}
