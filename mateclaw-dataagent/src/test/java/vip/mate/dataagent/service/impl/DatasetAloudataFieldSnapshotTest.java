package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.dto.DatasetCreateRequest;
import vip.mate.dataagent.dto.DatasetSourceDefinition;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasetFieldEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.*;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetAloudataFieldSnapshotTest {
    @Mock DatasetMapper datasetMapper;
    @Mock DatasetFieldMapper datasetFieldMapper;
    @Mock DatasetDataMapper datasetDataMapper;
    @Mock DatasourceMapper datasourceMapper;
    @Mock DatasourceTableMapper datasourceTableMapper;
    @Mock DatasourceColumnMapper datasourceColumnMapper;
    @Mock AloudataMetricMapper aloudataMetricMapper;
    @Mock AloudataDimensionMapper aloudataDimensionMapper;
    @Mock WorkspaceGuard workspaceGuard;
    @Mock DatasourceManageService datasourceManageService;

    @Test
    void createAloudataMetricsDatasetSnapshotsSelectedMetricAndDimensionDisplayNames() {
        DatasetManageServiceImpl service = new DatasetManageServiceImpl(
                datasetMapper, datasetFieldMapper, datasetDataMapper, datasourceMapper,
                datasourceTableMapper, datasourceColumnMapper, aloudataMetricMapper,
                aloudataDimensionMapper, new ObjectMapper(), workspaceGuard, datasourceManageService,
                mock(InsightDashboardMapper.class));
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(9L);
        datasource.setName("Aloudata");
        datasource.setSourceType("aloudata");
        when(datasourceMapper.selectById(9L)).thenReturn(datasource);
        when(workspaceGuard.currentWorkspaceId()).thenReturn(11L);
        when(workspaceGuard.currentUserId()).thenReturn(99L);
        doAnswer(invocation -> {
            ((DatasetEntity) invocation.getArgument(0)).setId(101L);
            return 1;
        }).when(datasetMapper).insert((DatasetEntity) any(DatasetEntity.class));
        when(datasetFieldMapper.selectCount(any())).thenReturn(2L);

        DatasetCreateRequest request = new DatasetCreateRequest();
        request.setName("table1");
        request.setSourceDefinition(new DatasetSourceDefinition.AloudataMetricsDefinition(
                9L, List.of("met_sales"), List.of("dim_shop"),
                Map.of("met_sales", "所选指标展示名", "dim_shop", "所选维度展示名")));
        service.createDataset(request);

        ArgumentCaptor<DatasetFieldEntity> fields = ArgumentCaptor.forClass(DatasetFieldEntity.class);
        verify(datasetFieldMapper, times(2)).insert(fields.capture());
        assertEquals(List.of("dim_shop:所选维度展示名", "met_sales:所选指标展示名"), fields.getAllValues().stream()
                .map(field -> field.getColumnName() + ":" + field.getColumnAlias())
                .toList());
        verifyNoInteractions(aloudataMetricMapper, aloudataDimensionMapper);
    }
}
