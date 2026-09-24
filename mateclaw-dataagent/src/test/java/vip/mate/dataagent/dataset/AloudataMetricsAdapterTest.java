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
    void convertsMetricsQueryTableColumnsIntoRows() throws Exception {
        DatasetMapper mapper = mock(DatasetMapper.class);
        AloudataService service = mock(AloudataService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("strategy metrics");
        dataset.setSourceType("ALOUDATA_METRICS");
        dataset.setDatasourceId(3L);
        dataset.setSourceConfig(objectMapper.writeValueAsString(Map.of(
                "dimensions", List.of("metric_time"),
                "metrics", List.of("digo_distr_count_1", "digo_strategy_cnt"))));
        when(mapper.selectById(7L)).thenReturn(dataset);
        AloudataMetricQueryResponse response = new AloudataMetricQueryResponse();
        response.setSuccess(true);
        response.setCode("200");
        AloudataMetricQueryResponse.MetricData data = new AloudataMetricQueryResponse.MetricData();
        data.setColumns(Map.of(
                "metric_time", List.of(column("2026-09-01"), column("2026-09-02")),
                "digo_distr_count_1", List.of(column(120), column(138)),
                "digo_strategy_cnt", List.of(column(6), column(7))));
        data.setTotal(2L);
        response.setData(data);
        when(service.queryMetrics(eq(3L), any())).thenReturn(response);

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, service, objectMapper);
        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)),
                new DatasetReadRequest(7L, "strategy metrics", List.of(), List.of(), 10, 0, Map.of()));

        assertEquals(2, batch.rows().size());
        assertEquals("2026-09-01", batch.rows().get(0).get("metric_time"));
        assertEquals(120, batch.rows().get(0).get("digo_distr_count_1"));
        assertEquals(7, batch.rows().get(1).get("digo_strategy_cnt"));
        assertEquals(2L, batch.totalCount());
    }

    private AloudataMetricQueryResponse.ColumnValue column(Object value) {
        AloudataMetricQueryResponse.ColumnValue column = new AloudataMetricQueryResponse.ColumnValue();
        column.setValue(value);
        return column;
    }

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
