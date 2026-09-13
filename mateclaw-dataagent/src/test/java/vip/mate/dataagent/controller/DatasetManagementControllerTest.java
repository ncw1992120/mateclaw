package vip.mate.dataagent.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.DatasetCreateRequest;
import vip.mate.dataagent.dto.DatasetSourceDefinition;
import vip.mate.dataagent.dto.DatasetUpdateRequest;
import vip.mate.dataagent.dto.DatasetVO;
import vip.mate.dataagent.service.DatasetExecutionService;
import vip.mate.dataagent.service.DatasetManageService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetManagementControllerTest {

    @Mock DatasetManageService datasetService;
    @Mock DatasetExecutionService executionService;
    @Mock WorkspaceGuard workspaceGuard;

    @Test
    void createForwardsTypedSourceDefinitionToService() {
        DataAgentDatasetController controller = new DataAgentDatasetController(datasetService, executionService, workspaceGuard);
        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("orders");
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select id from orders"));
        when(datasetService.createDataset(request)).thenReturn(new DatasetVO());

        assertNotNull(controller.create(request));
        verify(datasetService).createDataset(request);
    }

    @Test
    void updateForwardsTypedSourceDefinitionToService() {
        DataAgentDatasetController controller = new DataAgentDatasetController(datasetService, executionService, workspaceGuard);
        DatasetUpdateRequest request = new DatasetUpdateRequest();
        request.setSourceDefinition(new DatasetSourceDefinition.AloudataViewDefinition(8L, "view-1"));
        when(datasetService.updateDataset(7L, request)).thenReturn(new DatasetVO());

        assertNotNull(controller.update(7L, request));
        verify(datasetService).updateDataset(7L, request);
    }

    @Test
    void managementEndpointsDeclareMemberAndViewerBoundaries() throws Exception {
        RequireWorkspaceRole createRole = DataAgentDatasetController.class
                .getDeclaredMethod("create", DatasetCreateRequest.class)
                .getAnnotation(RequireWorkspaceRole.class);
        RequireWorkspaceRole listRole = DataAgentDatasetController.class
                .getDeclaredMethod("list")
                .getAnnotation(RequireWorkspaceRole.class);
        RequireWorkspaceRole deleteRole = DataAgentDatasetController.class
                .getDeclaredMethod("delete", Long.class)
                .getAnnotation(RequireWorkspaceRole.class);

        assertNotNull(createRole);
        assertNotNull(listRole);
        assertNotNull(deleteRole);
        org.junit.jupiter.api.Assertions.assertEquals(DataAgentConstants.WORKSPACE_ROLE_MEMBER, createRole.value());
        org.junit.jupiter.api.Assertions.assertEquals(DataAgentConstants.WORKSPACE_ROLE_VIEWER, listRole.value());
        org.junit.jupiter.api.Assertions.assertEquals(DataAgentConstants.WORKSPACE_ROLE_MEMBER, deleteRole.value());
    }

    @Test
    void everyDatasetManagementEndpointHasAnExplicitSecurityBoundary() {
        for (java.lang.reflect.Method method : DataAgentDatasetController.class.getDeclaredMethods()) {
            if (!org.springframework.http.ResponseEntity.class.isAssignableFrom(method.getReturnType())
                    && !vip.mate.common.result.R.class.isAssignableFrom(method.getReturnType())) continue;
            assertTrue(method.isAnnotationPresent(RequireWorkspaceRole.class),
                    () -> "missing workspace role on " + method.getName());
        }
    }
}
