package vip.mate.dataagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dataset.jdbc.SqlValidationService;
import vip.mate.dataagent.dataset.http.HttpApiRequestPolicy;
import vip.mate.dataagent.dataset.http.HttpApiDatasetAdapter;
import vip.mate.dataagent.dataset.file.FileDatasetAdapter;
import vip.mate.dataagent.dataset.AloudataAnalysisViewAdapter;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetComposerControllerTest {
    @Test
    void registersControlledApiDefinitionAndPersistsOnlyDeclaration() throws Exception {
        DatasourceMapper mapper = mock(DatasourceMapper.class); WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DatasourceEntity source = source(); source.setConnectionParams("{\"allowedHosts\":[\"e2e-http\"]}");
        when(mapper.selectById(7L)).thenReturn(source); when(guard.currentWorkspaceId()).thenReturn(11L); when(guard.currentUserId()).thenReturn(22L);
        DatasetComposerController controller = controller(mapper, guard);
        DatasetComposerController.ApiDefinitionRequest request = new DatasetComposerController.ApiDefinitionRequest();
        request.setDatasourceId("7"); request.setEndpoint("http://e2e-http:8080/orders"); request.setMethod("GET"); request.setAllowedQueryParams(java.util.List.of("status"));
        var result = controller.registerApiDefinition(request);
        assertNotNull(result.getData().get("apiDefinitionId"));
        assertTrue(source.getConnectionParams().contains("apiDefinitions"));
        assertTrue(source.getConnectionParams().contains("allowedQueryParams"));
        verify(mapper).updateById(source);
    }

    @Test
    void rejectsSensitiveHeadersBeforePersistence() {
        DatasourceMapper mapper = mock(DatasourceMapper.class); WorkspaceGuard guard = mock(WorkspaceGuard.class);
        DatasourceEntity source = source(); source.setConnectionParams("{\"allowedHosts\":[\"e2e-http\"]}");
        when(mapper.selectById(7L)).thenReturn(source); when(guard.currentWorkspaceId()).thenReturn(11L); when(guard.currentUserId()).thenReturn(22L);
        DatasetComposerController.ApiDefinitionRequest request = new DatasetComposerController.ApiDefinitionRequest();
        request.setDatasourceId("7"); request.setEndpoint("http://e2e-http:8080/orders"); request.setHeaders(Map.of("Authorization", "secret"));
        assertThrows(IllegalArgumentException.class, () -> controller(mapper, guard).registerApiDefinition(request));
        assertEquals("{\"allowedHosts\":[\"e2e-http\"]}", source.getConnectionParams());
    }

    private DatasetComposerController controller(DatasourceMapper mapper, WorkspaceGuard guard) {
        return new DatasetComposerController(mock(DatasetManageService.class), mock(SqlValidationService.class), new ObjectMapper(), guard,
                mapper, new HttpApiRequestPolicy(true), mock(AloudataService.class), mock(HttpApiDatasetAdapter.class), mock(FileDatasetAdapter.class), mock(AloudataAnalysisViewAdapter.class));
    }
    private DatasourceEntity source() { DatasourceEntity source = new DatasourceEntity(); source.setId(7L); source.setWorkspaceId(11L); source.setOwnerId(22L); source.setMetaShared(true); source.setHost("e2e-http"); return source; }
}
