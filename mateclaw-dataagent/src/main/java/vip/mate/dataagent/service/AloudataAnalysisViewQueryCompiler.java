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

        List<Map<String, String>> orders = new ArrayList<>();
        for (var order : request.orders()) {
            if (!allowedFields.contains(order.field())) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,
                        "指标视图不支持排序字段: " + order.field());
            }
            orders.add(Map.of(order.field(), order.direction()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("metrics", names(view.metrics()));
        body.put("dimensions", names(view.dimensions()));
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
