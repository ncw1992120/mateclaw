package vip.mate.dataagent.dto;

import java.util.List;
import java.util.Map;

/** 已有 Aloudata 指标视图的只读定义。 */
public record AloudataAnalysisViewDetail(
        String id,
        String viewName,
        String displayName,
        String description,
        List<Map<String, Object>> metrics,
        List<Map<String, Object>> dimensions,
        String timeConstraint,
        List<Map<String, Object>> filters,
        List<Map<String, Object>> resultFilters,
        List<Map<String, Object>> orders) {
    public AloudataAnalysisViewDetail {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        filters = filters == null ? List.of() : List.copyOf(filters);
        resultFilters = resultFilters == null ? List.of() : List.copyOf(resultFilters);
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
}
