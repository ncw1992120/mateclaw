package vip.mate.dataagent.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.aloudata.AloudataFilterExpressions;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasetFieldEntity;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasetFieldMapper;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.service.AloudataService;

import java.util.*;

/** Aloudata 指标&维度输入 Adapter；查询条件转换为 Aloudata 表达式。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AloudataMetricsAdapter implements DatasetSourceAdapter {
    private final DatasetMapper datasetMapper;
    private final AloudataMetricMapper aloudataMetricMapper;
    private final AloudataDimensionMapper aloudataDimensionMapper;
    private final DatasetFieldMapper datasetFieldMapper;
    private final AloudataService aloudataService;
    private final ObjectMapper mapper;

    @Override public boolean supports(DatasetSourceType sourceType) { return sourceType == DatasetSourceType.ALOUDATA_METRICS; }

    @Override public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId) {
        DatasetEntity dataset = require(context, datasetId);
        Map<String, Object> config = config(dataset);
        // 展示字段标题优先级：创建数据集时冻结的展示名快照（dataagent_dataset_field.columnAlias）
        // > 同步表实时值（metricDisplayName/dimDisplayName）> 技术名。
        // 快照让查询配置回显与同步表的后续变化解耦。
        Map<String, String> snapshot = snapshotDisplayNames(datasetId);
        Map<String, String> displayNames = aloudataDisplayNames(dataset.getDatasourceId());
        List<DatasetColumn> columns = new ArrayList<>();
        strings(config.get("dimensions")).forEach(name -> columns.add(new DatasetColumn(name,
                titleOf(name, snapshot, displayNames), "STRING", true, "dimension")));
        strings(config.get("metrics")).forEach(name -> columns.add(new DatasetColumn(name,
                titleOf(name, snapshot, displayNames), "DECIMAL", true, "measure")));
        return new DatasetInputDescriptor(datasetId, dataset.getName(), DatasetSourceType.ALOUDATA_METRICS, columns, dataset.getRowCount(), Map.of("datasourceId", dataset.getDatasourceId()), null);
    }

    private String titleOf(String name, Map<String, String> snapshot, Map<String, String> displayNames) {
        String title = snapshot.getOrDefault(name, displayNames.getOrDefault(name, name));
        return title == null || title.isBlank() ? name : title;
    }

    /** 创建数据集时冻结的展示名快照（columnAlias）；旧数据集无快照返回空 Map。 */
    private Map<String, String> snapshotDisplayNames(long datasetId) {
        try {
            Map<String, String> result = new HashMap<>();
            datasetFieldMapper.selectList(new LambdaQueryWrapper<DatasetFieldEntity>()
                            .eq(DatasetFieldEntity::getDatasetId, datasetId)
                            .eq(DatasetFieldEntity::getDeleted, 0))
                    .forEach(field -> putIfNotBlank(result, field.getColumnName(), field.getColumnAlias()));
            return result;
        } catch (Exception e) {
            log.warn("[Aloudata指标&维度] 读取展示名快照失败 datasetId={}: {}", datasetId, e.getMessage());
            return Map.of();
        }
    }

    /** 技术名 → 平台展示名映射；元数据查询失败不阻断数据集描述。 */
    private Map<String, String> aloudataDisplayNames(Long datasourceId) {
        Map<String, String> result = new HashMap<>();
        try {
            aloudataMetricMapper.selectList(new LambdaQueryWrapper<AloudataMetricEntity>()
                            .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                            .select(AloudataMetricEntity::getMetricName, AloudataMetricEntity::getMetricDisplayName))
                    .forEach(metric -> putIfNotBlank(result, metric.getMetricName(), metric.getMetricDisplayName()));
            aloudataDimensionMapper.selectList(new LambdaQueryWrapper<AloudataDimensionEntity>()
                            .eq(AloudataDimensionEntity::getDatasourceId, datasourceId)
                            .select(AloudataDimensionEntity::getDimName, AloudataDimensionEntity::getDimDisplayName))
                    .forEach(dim -> putIfNotBlank(result, dim.getDimName(), dim.getDimDisplayName()));
        } catch (Exception e) {
            log.warn("[Aloudata指标&维度] 读取平台展示名失败 datasourceId={}: {}", datasourceId, e.getMessage());
        }
        return result;
    }

    private void putIfNotBlank(Map<String, String> map, String name, String displayName) {
        if (name != null && !name.isBlank() && displayName != null && !displayName.isBlank()) map.put(name, displayName);
    }

    @Override public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        DatasetEntity dataset = require(context, request.datasetId());
        Map<String, Object> config = config(dataset);
        AloudataMetricQueryRequest query = new AloudataMetricQueryRequest();
        List<String> configuredMetrics = strings(config.get("metrics"));
        List<String> configuredDimensions = strings(config.get("dimensions"));
        // 指标仍使用数据集配置；分组维度只使用本次展示字段中的维度。
        // 隐藏维度可用于筛选，但不能继续参与 GROUP BY，否则 KPI 会被拆成多行。
        List<String> metrics = configuredMetrics;
        List<String> dimensions = requestedFields(configuredDimensions, request.columns());
        query.setMetrics(metrics); query.setDimensions(dimensions);
        // metric_time（分区字段）条件生成单引号 DateTrunc/Cast 专用形态进 filters
        // （demo 环境实测通过；无引号/双引号字段一律 SM_02_0006/0014，见 AloudataFilterExpressions 类注释）。
        query.setFilters(AloudataFilterExpressions.combineMetricTimeExpressions(
                request.filters().stream().map(filter -> expression(filter, request.timeGranularity())).toList()));
        // orders 仅当字段属于已选指标/维度时下发；Aloudata 要求排序字段包含在 metrics/dimensions 中
        List<Map<String, String>> orders = ordersExpression(metrics, dimensions, request.orders());
        query.setOrders(orders.isEmpty() ? null : orders);
        int limit = request.limit() == null ? 100 : request.limit();
        int offset = request.offset() == null ? 0 : request.offset();
        query.setLimit(limit); query.setOffset(offset);
        boolean countRequested = request.requestTotalCount();
        query.setIsQueryTotalCount(countRequested);
        query.setQueryResultType("DATA");
        AloudataMetricQueryResponse response = aloudataService.queryMetrics(dataset.getDatasourceId(), query);
        requireSuccess(response);
        List<Map<String, Object>> rows = AloudataMetricRows.from(response);
        Long total = response != null && response.getData() != null ? response.getData().getTotal() : null;
        boolean totalKnown = total != null;
        // 新版接口返回 total 时按精确总数判断；旧版没有 total 时只能用“是否满页”判断。
        boolean hasNext = totalKnown ? offset + rows.size() < total : rows.size() >= limit;
        return new DatasetBatch(rows, null, rows.size(), !hasNext,
                new PushdownReport(request.filters(), List.of(), orders.isEmpty() ? List.of() : request.orders(),
                        true, true, countRequested, null), totalKnown ? total : null);
    }

    private void requireSuccess(AloudataMetricQueryResponse response) {
        if (response != null && response.hasBusinessFailure()) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,
                    response.failureDescription());
        }
    }

    private List<Map<String, String>> ordersExpression(List<String> metrics, List<String> dimensions,
                                                       List<DatasetSort> orders) {
        if (orders == null || orders.isEmpty()) return List.of();
        Set<String> selectable = new LinkedHashSet<>();
        selectable.addAll(metrics);
        selectable.addAll(dimensions);
        List<Map<String, String>> result = new ArrayList<>();
        for (DatasetSort sort : orders) {
            // 不在已选指标/维度中的排序字段不下发（残余由上层结果处理），避免 Aloudata 直接报错
            if (selectable.contains(sort.field())) {
                result.add(Map.of(sort.field(), sort.direction()));
            }
        }
        return result;
    }

    private List<String> requestedFields(List<String> configuredFields, List<String> requestedColumns) {
        if (requestedColumns == null || requestedColumns.isEmpty()) return configuredFields;
        Set<String> requested = new HashSet<>(requestedColumns);
        return configuredFields.stream().filter(requested::contains).toList();
    }

    private DatasetEntity require(DatasetAccessContext context, long id) {
        if (context == null || !context.canRead(id)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        DatasetEntity dataset = datasetMapper.selectById(id);
        if (dataset == null || !DatasetSourceType.ALOUDATA_METRICS.name().equalsIgnoreCase(dataset.getSourceType())) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是 Aloudata 指标&维度类型");
        return dataset;
    }
    private Map<String, Object> config(DatasetEntity dataset) { try { return mapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {}); } catch (Exception e) { return Map.of(); } }
    private List<String> strings(Object value) { return value == null ? List.of() : mapper.convertValue(value, new TypeReference<>() {}); }

    /** 保留既有普通维度过滤器的 wire 语法，仅将 metric_time 委托给分区字段专用编译器。 */
    private String expression(DatasetFilter filter, String timeGranularity) {
        if (AloudataFilterExpressions.METRIC_TIME_FIELD.equals(filter.field())) {
            return AloudataFilterExpressions.of(filter, timeGranularity);
        }
        String field = "[" + filter.field() + "]";
        String operator = switch (filter.operator().toLowerCase(Locale.ROOT)) {
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
            case "contains" -> throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                    "Aloudata 语义层不支持 contains 过滤，请改用等值/集合条件");
            default -> throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                    "Aloudata 指标查询不支持过滤操作: " + filter.operator());
        };
        if (filter.value() instanceof Collection<?> values) {
            return field + " " + operator + " (" + values.stream().map(this::literal)
                    .collect(java.util.stream.Collectors.joining(",")) + ")";
        }
        return field + " " + operator + " (" + literal(filter.value()) + ")";
    }

    private String literal(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
