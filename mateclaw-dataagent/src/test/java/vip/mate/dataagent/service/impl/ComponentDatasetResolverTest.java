package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComponentDatasetResolverTest {
    private final DatasetMapper datasets = mock(DatasetMapper.class);
    private final WorkspaceGuard guard = mock(WorkspaceGuard.class);
    private final ComponentDatasetResolver resolver = new ComponentDatasetResolver(datasets, guard);
    private final ObjectMapper mapper = new ObjectMapper();

    ComponentDatasetResolverTest() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "scope-test"), DatasetEntity.class);
        when(guard.currentWorkspaceId()).thenReturn(11L);
    }

    @Test
    void resolvesTheCurrentIdUsingWorkspaceDashboardComponentAndAlias() throws Exception {
        var inputs = mapper.readTree("[{\"datasetId\":\"99\",\"inputName\":\"table_ab1\"}]");
        DatasetEntity current = new DatasetEntity(); current.setId(101L);
        when(datasets.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DatasetEntity> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            for (String column : new String[]{"workspace_id", "dashboard_id", "component_id", "name", "deleted"}) assertTrue(sql.contains(column));
            assertTrue(query.getParamNameValuePairs().values().containsAll(java.util.List.of(11L, 20L, "card-1", "table_ab1", 0)));
            return current;
        });
        resolver.resolve(20L, "card-1", inputs);
        assertEquals("101", inputs.get(0).path("datasetId").asText());
        verify(datasets, never()).selectById(99L);
    }

    @Test
    void doesNotFallbackToAnotherComponentOrAnOldAlias() throws Exception {
        var inputs = mapper.readTree("[{\"datasetId\":\"99\",\"inputName\":\"table_ab\"}]");
        DatasetEntity other = new DatasetEntity(); other.setId(99L); other.setWorkspaceId(11L);
        other.setDashboardId(20L); other.setComponentId("card-2");
        when(datasets.selectById(99L)).thenReturn(other);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(20L, "card-1", inputs));
    }

    @Test
    void retainsExplicitLegacyPublicReferencesOnlyWithinTheWorkspace() throws Exception {
        var inputs = mapper.readTree("[{\"datasetId\":\"99\",\"inputName\":\"table_ab\"}]");
        DatasetEntity legacy = new DatasetEntity(); legacy.setId(99L); legacy.setWorkspaceId(11L);
        when(datasets.selectById(99L)).thenReturn(legacy);
        assertDoesNotThrow(() -> resolver.resolve(20L, "card-1", inputs));
        legacy.setWorkspaceId(12L);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(20L, "card-1", inputs));
    }
}
