package vip.mate.dataagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.auth.crypto.TransportCryptoService;
import vip.mate.dataagent.dto.DatasourceVO;
import vip.mate.dataagent.auth.service.PermissionChecker;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.*;
import vip.mate.dataagent.service.impl.DatasourceManageServiceImpl;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.auth.service.WorkspaceGuard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasourceManageServiceSecurityTest {
    @Mock DatasourceMapper datasourceMapper;
    @Mock DatasourceTableMapper datasourceTableMapper;
    @Mock DatasourceColumnMapper datasourceColumnMapper;
    @Mock ResourceGrantMapper resourceGrantMapper;
    @Mock AloudataService aloudataService;
    @Mock AloudataConfigHelper aloudataConfigHelper;
    @Mock PermissionChecker permissionChecker;
    @Mock WorkspaceGuard workspaceGuard;
    @Mock TransportCryptoService transportCryptoService;

    @Test
    void datasourceResponseRedactsSensitiveConnectionParams() {
        DatasourceEntity entity = new DatasourceEntity();
        entity.setId(7L);
        entity.setName("warehouse");
        entity.setWorkspaceId(11L);
        entity.setOwnerId(99L);
        entity.setConnectionParams("{\"schema\":\"sales\",\"password\":\"p\",\"authValue\":\"uid-secret\",\"headers\":{\"Authorization\":\"Bearer secret\"}}");
        when(datasourceMapper.selectById(7L)).thenReturn(entity);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        when(datasourceTableMapper.selectCount(any())).thenReturn(0L);

        DatasourceVO response = service().getDatasource(7L);

        assertNotNull(response);
        assertNotNull(response.getConnectionParams());
        assertTrue(response.getConnectionParams().contains("sales"));
        assertFalse(response.getConnectionParams().contains("uid-secret"));
        assertFalse(response.getConnectionParams().contains("Bearer secret"));
        assertFalse(response.getConnectionParams().contains("\"password\""));
    }

    private DatasourceManageServiceImpl service() {
        return new DatasourceManageServiceImpl(datasourceMapper, datasourceTableMapper, datasourceColumnMapper,
                resourceGrantMapper, aloudataService, aloudataConfigHelper, permissionChecker, workspaceGuard,
                transportCryptoService);
    }
}
