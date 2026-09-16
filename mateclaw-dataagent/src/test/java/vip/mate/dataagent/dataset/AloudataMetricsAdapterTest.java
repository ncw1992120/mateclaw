package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.service.AloudataService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AloudataMetricsAdapterTest {
    @Test
    void describesAndPushesFiltersForMetricsDataset() throws Exception {
        DatasetMapper mapper = mock(DatasetMapper.class); AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity(); dataset.setId(7L); dataset.setName("sales metrics"); dataset.setSourceType("ALOUDATA_METRICS"); dataset.setDatasourceId(3L);
        dataset.setSourceConfig(new ObjectMapper().writeValueAsString(Map.of("dimensions", List.of("region"), "metrics", List.of("revenue"))));
        when(mapper.selectById(7L)).thenReturn(dataset);
        AloudataMetricQueryResponse response = new AloudataMetricQueryResponse();
        when(service.queryMetrics(eq(3L), any())).thenReturn(response);
        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, service, new ObjectMapper());
        DatasetInputDescriptor descriptor = adapter.describe(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)), 7L);
        assertEquals(List.of("region", "revenue"), descriptor.schema().stream().map(DatasetColumn::name).toList());
        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)), new DatasetReadRequest(7L, "metrics", List.of(), List.of(new DatasetFilter("region", "dimension", "eq", "east")), 10, 0, Map.of()));
        assertEquals(1, batch.pushdownReport().pushedFilters().size()); verify(service).queryMetrics(eq(3L), any());
    }
}
