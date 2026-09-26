package vip.mate.dataagent.service;

import org.springframework.stereotype.Component;

import vip.mate.dataagent.aloudata.AloudataFilterExpressions;
import vip.mate.dataagent.dataset.DatasetFilter;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadRequest;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;

import java.util.*;

/**
 * 将已存在的指标视图定义编译为 Semantic metrics/query 请求。
 * <p>
 * 关键约束（demo 实测，勿改回结构化形态）：{@code filters} 必须是**表达式字符串数组**，
 * 形如 {@code ["[region] = \"华东\""]}；传 {@code {field,operator,value}} 结构化对象真实
 * 服务会返回 {@code SM99002 系统异常}。语法支持 {@code = <> > >= < <= IN(...) NotIn(...)}
 * 与 {@code AND/OR/()}。
 */
@Component
public class AloudataAnalysisViewQueryCompiler {
    public Map<String, Object> compile(AloudataAnalysisViewDetail view, DatasetReadRequest request) {
        if (view == null) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "指标视图定义不能为空");
        }
        Set<String> allowedFields = new HashSet<>();
        view.metrics().forEach(m -> addNames(allowedFields, m));
        view.dimensions().forEach(d -> addNames(allowedFields, d));
        List<String> filters = new ArrayList<>();
        for (DatasetFilter filter : request.filters()) {
            if (!allowedFields.contains(filter.field())) {
                throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                        "指标视图不支持字段筛选: " + filter.field());
            }
            filters.add(toExpression(filter));
        }

        List<String> projectedColumns = resolveColumns(view, request.columns());
        Set<String> projectedSet = new LinkedHashSet<>(projectedColumns);

        List<Map<String, String>> orders = new ArrayList<>();
        for (var order : request.orders()) {
            if (!allowedFields.contains(order.field()) || !projectedSet.contains(order.field())) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,
                        "排序字段未包含在展示字段中: " + order.field());
            }
            orders.add(Map.of(order.field(), order.direction()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("metrics", selectedNames(view.metrics(), projectedSet));
        body.put("dimensions", selectedNames(view.dimensions(), projectedSet));
        if (!filters.isEmpty()) body.put("filters", filters);
        if (view.timeConstraint() != null && !view.timeConstraint().isBlank()) {
            body.put("timeConstraint", view.timeConstraint());
        }
        if (request.limit() != null) body.put("limit", request.limit());
        if (request.offset() != null) body.put("offset", request.offset());
        if (!orders.isEmpty()) body.put("orders", orders);
        if (request.requestTotalCount()) body.put("isQueryTotalCount", true);
        body.put("queryResultType", "DATA");
        return Map.copyOf(body);
    }

    /** 展示列是输出契约；其余指标/维度不进入查询，时间筛选字段可只过滤而不参与分组。 */
    public List<String> resolveColumns(AloudataAnalysisViewDetail view, List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return java.util.stream.Stream.concat(names(view.dimensions()).stream(), names(view.metrics()).stream()).toList();
        }
        List<Map<String, Object>> definitions = new ArrayList<>();
        definitions.addAll(view.dimensions());
        definitions.addAll(view.metrics());
        List<String> resolved = new ArrayList<>();
        for (String requested : columns) {
            Map<String, Object> definition = definitions.stream()
                    .filter(item -> aliases(item).contains(requested))
                    .findFirst()
                    .orElseThrow(() -> new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,
                            "展示字段不属于当前指标视图: " + requested));
            String canonical = canonicalName(definition);
            if (!resolved.contains(canonical)) resolved.add(canonical);
        }
        return List.copyOf(resolved);
    }

    private List<String> selectedNames(List<Map<String, Object>> definitions, Set<String> selected) {
        return definitions.stream()
                .map(this::canonicalName)
                .filter(selected::contains)
                .toList();
    }

    private Set<String> aliases(Map<String, Object> definition) {
        Set<String> result = new LinkedHashSet<>();
        addNames(result, definition);
        return result;
    }

    private String canonicalName(Map<String, Object> definition) {
        return List.of("name", "metricName", "dimName", "code").stream()
                .map(definition::get)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,
                        "指标视图包含无字段名的定义"));
    }

    /** 单个下推条件 → Aloudata 筛选表达式；无法等价表达的运算符直接拒绝，不静默丢弃。 */
    private String toExpression(DatasetFilter filter) {
        String operator = filter.operator() == null ? "" : filter.operator().toLowerCase(Locale.ROOT);
        if ("is_null".equals(operator) || "is_not_null".equals(operator)) {
            throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                    "指标视图不支持该筛选运算符: " + filter.operator());
        }
        try {
            return AloudataFilterExpressions.of(filter);
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER, e.getMessage());
        }
    }

    private void addNames(Set<String> names, Map<String, Object> definition) {
        for (String key : List.of("name", "metricName", "dimName", "code")) {
            Object value = definition.get(key);
            if (value != null && !String.valueOf(value).isBlank()) names.add(String.valueOf(value));
        }
    }

    private List<String> names(List<Map<String, Object>> definitions) {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> definition : definitions) {
            for (String key : List.of("name", "metricName", "dimName", "code")) {
                Object value = definition.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    result.add(String.valueOf(value));
                    break;
                }
            }
        }
        return List.copyOf(result);
    }
}
