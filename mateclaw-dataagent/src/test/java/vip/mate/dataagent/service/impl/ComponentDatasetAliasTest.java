package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.*;
import vip.mate.dataagent.repository.*;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComponentDatasetAliasTest {
    private final DatasetMapper datasets = mock(DatasetMapper.class);
    private final WorkspaceGuard guard = mock(WorkspaceGuard.class);
    private final InsightDashboardMapper dashboards = mock(InsightDashboardMapper.class);
    private final DatasourceMapper sources = mock(DatasourceMapper.class);
    private final DatasetManageServiceImpl service = new DatasetManageServiceImpl(
            datasets, mock(DatasetFieldMapper.class), mock(DatasetDataMapper.class), sources,
            mock(DatasourceTableMapper.class), mock(DatasourceColumnMapper.class),
            mock(AloudataMetricMapper.class), mock(AloudataDimensionMapper.class),
            new ObjectMapper(), guard, mock(DatasourceManageService.class), dashboards);
    private final List<DatasetEntity> stored = new ArrayList<>();

    ComponentDatasetAliasTest() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "alias-test"), DatasetEntity.class);
        when(guard.currentWorkspaceId()).thenReturn(11L);
        when(guard.currentUserId()).thenReturn(99L);
        InsightDashboardEntity dashboard = new InsightDashboardEntity();
        dashboard.setId(20L); dashboard.setWorkspaceId(11L); dashboard.setOwnerId(99L);
        when(dashboards.selectById(20L)).thenReturn(dashboard);
        DatasourceEntity source = new DatasourceEntity();
        source.setId(3L); source.setSourceType("postgresql");
        when(sources.selectById(3L)).thenReturn(source);
        when(datasets.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DatasetEntity> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("workspace_id"));
            assertTrue(sql.contains("dashboard_id"));
            assertTrue(sql.contains("component_id"));
            var values = query.getParamNameValuePairs().values();
            return stored.stream().filter(dataset -> values.contains(dataset.getName())
                    && values.contains(dataset.getComponentId())
                    && values.contains(dataset.getDashboardId())).count();
        });
        doAnswer(invocation -> {
            DatasetEntity entity = invocation.getArgument(0);
            entity.setId(100L + stored.size()); stored.add(entity); return 1;
        }).when(datasets).insert(any(DatasetEntity.class));
    }

    private DatasetCreateRequest input(String componentId) {
        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("table_ab"); request.setDashboardId(20L); request.setComponentId(componentId);
        request.setSourceDefinition(new DatasetSourceDefinition.JdbcSqlDefinition(3L, "select 1 as amount"));
        return request;
    }

    @Test
    void differentComponentsCanPersistTheSameAliasAndSameComponentCannot() {
        DatasetVO first = service.createDataset(input("card-1"));
        DatasetVO second = service.createDataset(input("card-2"));
        assertNotEquals(first.getId(), second.getId());
        assertEquals("card-2", second.getComponentId());
        assertEquals(20L, second.getDashboardId());
        assertEquals("同一组件内数据集别名已存在: table_ab",
                assertThrows(IllegalArgumentException.class,
                        () -> service.createDataset(input("card-1"))).getMessage());
        assertEquals(2, stored.size());
    }

    @Test
    void rejectsIncompleteOrForeignWorkspaceScope() {
        DatasetCreateRequest request = input(null);
        assertThrows(IllegalArgumentException.class, () -> service.createDataset(request));
        InsightDashboardEntity foreign = new InsightDashboardEntity();
        foreign.setWorkspaceId(12L);
        when(dashboards.selectById(20L)).thenReturn(foreign);
        assertThrows(vip.mate.dataagent.exception.BusinessException.class,
                () -> service.createDataset(input("card-1")));
        verify(datasets, never()).insert(any(DatasetEntity.class));
    }

    @Test
    void renamingUsesTheStoredComponentScopeAndExcludesTheDatasetItself() {
        service.createDataset(input("card-1"));
        DatasetEntity other = new DatasetEntity();
        other.setId(200L); other.setWorkspaceId(11L); other.setOwnerId(99L);
        other.setDashboardId(20L); other.setComponentId("card-1"); other.setName("table_wd");
        when(datasets.selectById(200L)).thenReturn(other);
        DatasetUpdateRequest rename = new DatasetUpdateRequest();
        rename.setName("table_ab");

        assertEquals("同一组件内数据集别名已存在: table_ab",
                assertThrows(IllegalArgumentException.class, () -> service.updateDataset(200L, rename)).getMessage());
        verify(datasets, never()).updateById(any(DatasetEntity.class));
    }
}
