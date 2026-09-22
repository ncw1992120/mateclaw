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
        // orders 仅当字段属于已选指标/维度时下发；Aloudata 要求排序字段包含在 metrics/dimensions 中
        List<Map<String, String>> orders = ordersExpression(config, request.orders());
        query.setOrders(orders.isEmpty() ? null : orders);
        query.setLimit(request.limit() == null ? 100 : request.limit()); query.setOffset(request.offset() == null ? 0 : request.offset());
        query.setIsQueryTotalCount(request.requestTotalCount());
        query.setQueryResultType("DATA");
        AloudataMetricQueryResponse response = aloudataService.queryMetrics(dataset.getDatasourceId(), query);
        requireSuccess(response);
        List<Map<String, Object>> rows = response == null || response.getData() == null || response.getData().getRows() == null ? List.of() : response.getData().getRows();
        Long total = response != null && response.getData() != null ? response.getData().getTotal() : null;
        boolean totalRequested = Boolean.TRUE.equals(request.requestTotalCount()) && total != null;
        return new DatasetBatch(rows, null, rows.size(), true,
                new PushdownReport(request.filters(), List.of(), orders.isEmpty() ? List.of() : request.orders(),
                        true, true, totalRequested, null), totalRequested ? total : null);
    }

    private void requireSuccess(AloudataMetricQueryResponse response) {
        if (response == null) return;
        // Aloudata 包络：code 是字符串 "200"，错误信息在 errorMsg/detailErrorMsg
        if (response.getCode() != null && !"200".equals(response.getCode())) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,
                    "Aloudata 指标查询失败: " + response.getErrorMsg());
        }
    }

    private List<Map<String, String>> ordersExpression(Map<String, Object> config, List<DatasetSort> orders) {
        if (orders == null || orders.isEmpty()) return List.of();
        Set<String> selectable = new LinkedHashSet<>();
        selectable.addAll(strings(config.get("metrics")));
        selectable.addAll(strings(config.get("dimensions")));
        List<Map<String, String>> result = new ArrayList<>();
        for (DatasetSort sort : orders) {
            // 不在已选指标/维度中的排序字段不下发（残余由上层结果处理），避免 Aloudata 直接报错
            if (selectable.contains(sort.field())) {
                result.add(Map.of(sort.field(), sort.direction()));
            }
        }
        return result;
    }

    private DatasetEntity require(DatasetAccessContext context, long id) {
        if (context == null || !context.canRead(id)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        DatasetEntity dataset = datasetMapper.selectById(id);
        if (dataset == null || !DatasetSourceType.ALOUDATA_METRICS.name().equalsIgnoreCase(dataset.getSourceType())) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是 Aloudata 指标&维度类型");
        return dataset;
    }
    private Map<String, Object> config(DatasetEntity dataset) { try { return mapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {}); } catch (Exception e) { return Map.of(); } }
    private List<String> strings(Object value) { return value == null ? List.of() : mapper.convertValue(value, new TypeReference<>() {}); }

    /** 条件转 Aloudata 表达式：集合值逐项展开（[field] IN ("A","B")），标量值单值括号。 */
    String expression(DatasetFilter filter) {
        String field = "[" + filter.field() + "]";
        String op = filter.operator().toUpperCase(Locale.ROOT).replace("_IN", " IN").replace("_NULL", " NULL");
        op = switch (filter.operator().toLowerCase(Locale.ROOT)) {
            case "eq" -> "=";
            case "neq" -> "<>";
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            case "in" -> "IN";
            case "not_in" -> "NOT IN";
            case "between" -> "BETWEEN";
            case "is_null" -> "IS NULL";
            case "is_not_null" -> "IS NOT NULL";
            case "contains" -> "IN";
            default -> throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                    "Aloudata 指标查询不支持过滤操作: " + filter.operator());
        };
        if ("contains".equals(filter.operator().toLowerCase(Locale.ROOT))) {
            // 语义层不支持 LIKE：contains 退化为对包含该子串的精确集合匹配不可行 → 明确失败而不是丢语义
            throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                    "Aloudata 语义层不支持 contains 过滤，请改用等值/集合条件");
        }
        String values;
        if (filter.value() instanceof Collection<?> collection) {
            values = collection.stream().map(v -> "\"" + String.valueOf(v).replace("\"", "\\\"") + "\"")
                    .collect(java.util.stream.Collectors.joining(","));
        } else {
            values = "\"" + String.valueOf(filter.value()).replace("\"", "\\\"") + "\"";
        }
        return field + " " + op + " (" + values + ")";
    }
}
