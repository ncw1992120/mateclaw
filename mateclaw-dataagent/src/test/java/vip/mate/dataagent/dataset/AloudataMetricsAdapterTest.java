package vip.mate.dataagent.dataset;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.service.AloudataService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AloudataMetricsAdapterTest {

    /**
     * describe() 用 LambdaQueryWrapper 按实体列查询展示名；Lambda 列解析依赖
     * TableInfo 缓存，纯单测无 MyBatis 启动流程，这里手工注册两个实体。
     */
    @BeforeAll
    static void initEntityTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AloudataMetricEntity.class);
        TableInfoHelper.initTableInfo(assistant, AloudataDimensionEntity.class);
    }

    /** 新构造签名共用：mock 元数据 mapper（默认无同步数据，展示名回退技术名）。 */
    @SuppressWarnings("unchecked")
    private AloudataMetricMapper emptyMetricMapper() {
        AloudataMetricMapper mapper = mock(AloudataMetricMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        return mapper;
    }

    @SuppressWarnings("unchecked")
    private AloudataDimensionMapper emptyDimensionMapper() {
        AloudataDimensionMapper mapper = mock(AloudataDimensionMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        return mapper;
    }

    @Test
    void combinesMetricTimePartitionBoundsIntoOneFilterExpression() throws Exception {
        DatasetMapper mapper = mock(DatasetMapper.class);
        AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("metrics");
        dataset.setSourceType("ALOUDATA_METRICS");
        dataset.setDatasourceId(3L);
        dataset.setSourceConfig(new ObjectMapper().writeValueAsString(Map.of(
                "metrics", List.of("revenue"), "dimensions", List.of("metric_time"))));
        when(mapper.selectById(7L)).thenReturn(dataset);
        when(service.queryMetrics(eq(3L), any())).thenReturn(null);

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, emptyMetricMapper(), emptyDimensionMapper(), service, new ObjectMapper());
        adapter.read(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)), new DatasetReadRequest(
                7L, "metrics", List.of(), List.of(
                        new DatasetFilter("metric_time", "dimension", "gte", "2026-09-01"),
                        new DatasetFilter("metric_time", "dimension", "lt", "2026-09-18")),
                20, 0, Map.of()));

        ArgumentCaptor<AloudataMetricQueryRequest> request = ArgumentCaptor.forClass(AloudataMetricQueryRequest.class);
        verify(service).queryMetrics(eq(3L), request.capture());
        assertEquals(List.of("(DateTrunc(['metric_time'], \"DAY\") >= (DateTrunc(Cast(\"2026-09-01 00:00:00\", \"TIMESTAMP\"), \"DAY\")))"
                        + " AND (DateTrunc(['metric_time'], \"DAY\") < (DateTrunc(Cast(\"2026-09-18 00:00:00\", \"TIMESTAMP\"), \"DAY\")))"),
                request.getValue().getFilters());
        assertNull(request.getValue().getTimeConstraint());
    }

    @Test
    void reportsAloudataBusinessErrorWhenOnlyDetailErrorIsReturned() throws Exception {
        DatasetMapper mapper = mock(DatasetMapper.class);
        AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("metrics");
        dataset.setSourceType("ALOUDATA_METRICS");
        dataset.setDatasourceId(3L);
        dataset.setSourceConfig(new ObjectMapper().writeValueAsString(Map.of("metrics", List.of("revenue"), "dimensions", List.of())));
        when(mapper.selectById(7L)).thenReturn(dataset);
        AloudataMetricQueryResponse response = new ObjectMapper().readValue(
                "{\"code\":\"500\",\"detailErrorMsg\":\"metric query rejected\",\"traceId\":\"trace-123\"}",
                AloudataMetricQueryResponse.class);
        when(service.queryMetrics(eq(3L), any())).thenReturn(response);

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, emptyMetricMapper(), emptyDimensionMapper(), service, new ObjectMapper());
        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task", Set.of(7L)),
                new DatasetReadRequest(7L, "metrics", List.of("revenue"), List.of(), 20, 0, Map.of())));

        assertTrue(error.getMessage().contains("metric query rejected"));
        assertTrue(error.getMessage().contains("trace-123"));
    }

    @Test
    void reportsAloudataBusinessErrorWhenOnlyMessageIsReturned() throws Exception {
        DatasetMapper mapper = mock(DatasetMapper.class);
        AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(7L);
        dataset.setName("metrics");
        dataset.setSourceType("ALOUDATA_METRICS");
        dataset.setDatasourceId(3L);
        dataset.setSourceConfig(new ObjectMapper().writeValueAsString(Map.of("metrics", List.of("revenue"), "dimensions", List.of())));
        when(mapper.selectById(7L)).thenReturn(dataset);
        AloudataMetricQueryResponse response = new ObjectMapper().readValue(
                "{\"code\":\"SM_02_0006\",\"message\":\"metric_time partition invalid\",\"traceId\":\"trace-message\"}",
                AloudataMetricQueryResponse.class);
        when(service.queryMetrics(eq(3L), any())).thenReturn(response);

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, emptyMetricMapper(), emptyDimensionMapper(), service, new ObjectMapper());
        DatasetReadException error = assertThrows(DatasetReadException.class, () -> adapter.read(
                new DatasetAccessContext(1L, 2L, "task", Set.of(7L)),
                new DatasetReadRequest(7L, "metrics", List.of("revenue"), List.of(), 20, 0, Map.of())));

        assertTrue(error.getMessage().contains("metric_time partition invalid"));
        assertTrue(error.getMessage().contains("trace-message"));
    }

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

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, emptyMetricMapper(), emptyDimensionMapper(), service, objectMapper);
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
        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, emptyMetricMapper(), emptyDimensionMapper(), service, new ObjectMapper());
        DatasetInputDescriptor descriptor = adapter.describe(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)), 7L);
        assertEquals(List.of("region", "revenue"), descriptor.schema().stream().map(DatasetColumn::name).toList());
        // 无同步元数据时展示名回退技术名
        assertEquals(List.of("region", "revenue"), descriptor.schema().stream().map(DatasetColumn::title).toList());
        DatasetBatch batch = adapter.read(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)), new DatasetReadRequest(7L, "metrics", List.of(), List.of(new DatasetFilter("region", "dimension", "eq", "east")), 10, 0, Map.of()));
        assertEquals(1, batch.pushdownReport().pushedFilters().size()); verify(service).queryMetrics(eq(3L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void describePrefersPlatformDisplayNamesOverTechnicalNames() throws Exception {
        DatasetMapper mapper = mock(DatasetMapper.class); AloudataService service = mock(AloudataService.class);
        DatasetEntity dataset = new DatasetEntity(); dataset.setId(7L); dataset.setName("sales metrics"); dataset.setSourceType("ALOUDATA_METRICS"); dataset.setDatasourceId(3L);
        dataset.setSourceConfig(new ObjectMapper().writeValueAsString(Map.of("dimensions", List.of("metric_time", "region"), "metrics", List.of("revenue"))));
        when(mapper.selectById(7L)).thenReturn(dataset);

        AloudataMetricMapper metricMetaMapper = mock(AloudataMetricMapper.class);
        AloudataMetricEntity metricMeta = new AloudataMetricEntity();
        metricMeta.setDatasourceId(3L); metricMeta.setMetricName("revenue"); metricMeta.setMetricDisplayName("收入金额");
        when(metricMetaMapper.selectList(any(Wrapper.class))).thenReturn(List.of(metricMeta));
        AloudataDimensionMapper dimensionMetaMapper = mock(AloudataDimensionMapper.class);
        AloudataDimensionEntity regionMeta = new AloudataDimensionEntity();
        regionMeta.setDatasourceId(3L); regionMeta.setDimName("region"); regionMeta.setDimDisplayName("地区");
        // 平台未配置中文名的维度（如系统分区字段）不进映射，回退技术名
        AloudataDimensionEntity metricTimeMeta = new AloudataDimensionEntity();
        metricTimeMeta.setDatasourceId(3L); metricTimeMeta.setDimName("metric_time"); metricTimeMeta.setDimDisplayName(null);
        when(dimensionMetaMapper.selectList(any(Wrapper.class))).thenReturn(List.of(regionMeta, metricTimeMeta));

        AloudataMetricsAdapter adapter = new AloudataMetricsAdapter(mapper, metricMetaMapper, dimensionMetaMapper, service, new ObjectMapper());
        DatasetInputDescriptor descriptor = adapter.describe(new DatasetAccessContext(1L, 2L, "task", Set.of(7L)), 7L);

        Map<String, String> titles = new HashMap<>();
        descriptor.schema().forEach(column -> titles.put(column.name(), column.title()));
        assertEquals("收入金额", titles.get("revenue"));
        assertEquals("地区", titles.get("region"));
        assertEquals("metric_time", titles.get("metric_time"));
    }
}
