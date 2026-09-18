package vip.mate.dataagent.dataset;

import java.util.List;
import java.util.Map;

/**
 * 统一读取请求；filters 是唯一的下推条件表达方式。
 * <p>
 * 字段名与展示名契约（定版见 docs/策略解读/字段名与展示名契约-实施计划.md §4.3）：
 * {@code filters[].field} 是<b>数据源字段名</b>（技术主键），不是用户改过的展示名；
 * 展示名只参与前端渲染与导出表头，下推链路一律使用字段名。
 */
public record DatasetReadRequest(
        long datasetId,
        String inputName,
        List<String> columns,
        List<DatasetFilter> filters,
        Integer limit,
        Integer offset,
        Map<String, Object> parameters) {
    public DatasetReadRequest {
        if (datasetId <= 0 || inputName == null || inputName.isBlank()) {
            throw new IllegalArgumentException("datasetId and inputName are required");
        }
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (limit != null && (limit <= 0 || limit > 1_000_000)) {
            throw new IllegalArgumentException("limit must be between 1 and 1000000");
        }
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
