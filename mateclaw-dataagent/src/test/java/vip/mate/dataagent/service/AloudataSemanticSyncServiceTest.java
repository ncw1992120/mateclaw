package vip.mate.dataagent.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.repository.AloudataCategoryMapper;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.service.impl.AloudataSemanticSyncServiceImpl;
import vip.mate.llm.service.ModelConfigService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AloudataSemanticSyncServiceTest {

    @Test
    void acceptsNumericAloudataTimestampsWhenBuildingMetricEntities() {
        AloudataSemanticSyncServiceImpl service = new AloudataSemanticSyncServiceImpl(
                mock(AloudataMetricMapper.class),
                mock(AloudataDimensionMapper.class),
                mock(AloudataMetricDimensionMapper.class),
                mock(AloudataCategoryMapper.class),
                mock(DatasourceMapper.class),
                mock(AloudataApiClient.class),
                mock(AloudataConfigHelper.class),
                mock(AloudataEndpointService.class),
                mock(AloudataSemanticEsService.class),
                mock(ModelConfigService.class),
                mock(AloudataService.class));

        Map<String, Object> metric = Map.of(
                "metricName", "metric_a",
                "gmtCreate", 1758100000000L,
                "gmtUpdate", 1758100000000L);

        @SuppressWarnings("unchecked")
        List<AloudataMetricEntity> entities = (List<AloudataMetricEntity>) ReflectionTestUtils.invokeMethod(
                service, "buildMetricEntities", 1L, List.of(metric), Map.of(), Map.of(), 1);

        assertEquals("1758100000000", entities.get(0).getGmtCreate());
        assertEquals("1758100000000", entities.get(0).getGmtUpdate());
    }
}
