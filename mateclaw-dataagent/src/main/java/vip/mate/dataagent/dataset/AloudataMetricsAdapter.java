package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.service.AloudataService;

import java.util.*;

/** Aloudata 指标&维度输入 Adapter；查询条件转换为 Aloudata 表达式。 */
@Component
@RequiredArgsConstructor
public class AloudataMetricsAdapter implements DatasetSourceAdapter {
    private final DatasetMapper datasetMapper;
    private final AloudataService aloudataService;
    private final ObjectMapper mapper;

    @Override public boolean supports(DatasetSourceType sourceType) { return sourceType == DatasetSourceType.ALOUDATA_METRICS; }

    @Override public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId) {
        DatasetEntity dataset = require(context, datasetId);
        Map<String, Object> config = config(dataset);
        List<DatasetColumn> columns = new ArrayList<>();
        strings(config.get("dimensions")).forEach(name -> columns.add(new DatasetColumn(name, name, "STRING", true, "dimension")));
        strings(config.get("metrics")).forEach(name -> columns.add(new DatasetColumn(name, name, "DECIMAL", true, "measure")));
        return new DatasetInputDescriptor(datasetId, dataset.getName(), DatasetSourceType.ALOUDATA_METRICS, columns, dataset.getRowCount(), Map.of("datasourceId", dataset.getDatasourceId()), null);
    }

    @Override public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        DatasetEntity dataset = require(context, request.datasetId());
        Map<String, Object> config = config(dataset);
        AloudataMetricQueryRequest query = new AloudataMetricQueryRequest();
        query.setMetrics(strings(config.get("metrics"))); query.setDimensions(strings(config.get("dimensions")));
        query.setFilters(request.filters().stream().map(this::expression).toList());
        query.setLimit(request.limit() == null ? 100 : request.limit()); query.setOffset(request.offset() == null ? 0 : request.offset());
        AloudataMetricQueryResponse response = aloudataService.queryMetrics(dataset.getDatasourceId(), query);
        List<Map<String, Object>> rows = response == null || response.getData() == null || response.getData().getRows() == null ? List.of() : response.getData().getRows();
        return new DatasetBatch(rows, null, rows.size(), true, new PushdownReport(request.filters(), List.of(), true, true, null));
    }

    private DatasetEntity require(DatasetAccessContext context, long id) {
        if (context == null || !context.canRead(id)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        DatasetEntity dataset = datasetMapper.selectById(id);
        if (dataset == null || !DatasetSourceType.ALOUDATA_METRICS.name().equalsIgnoreCase(dataset.getSourceType())) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是 Aloudata 指标&维度类型");
        return dataset;
    }
    private Map<String, Object> config(DatasetEntity dataset) { try { return mapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {}); } catch (Exception e) { return Map.of(); } }
    private List<String> strings(Object value) { return value == null ? List.of() : mapper.convertValue(value, new TypeReference<>() {}); }
    private String expression(DatasetFilter filter) { return "[" + filter.field() + "] " + filter.operator().toUpperCase(Locale.ROOT) + " (\"" + String.valueOf(filter.value()).replace("\"", "\\\"") + "\")"; }
}
