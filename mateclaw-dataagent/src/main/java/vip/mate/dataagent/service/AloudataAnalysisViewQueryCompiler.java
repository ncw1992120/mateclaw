package vip.mate.dataagent.service;

import org.springframework.stereotype.Component;

import vip.mate.dataagent.dataset.DatasetFilter;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadRequest;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;

import java.util.*;

/** 将已存在的指标视图定义编译为 Semantic metrics/query 请求。 */
@Component
public class AloudataAnalysisViewQueryCompiler {
    public Map<String, Object> compile(AloudataAnalysisViewDetail view, DatasetReadRequest request) {
        if (view == null) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "指标视图定义不能为空");
        }
        Set<String> allowedFields = new HashSet<>();
        view.metrics().forEach(m -> addNames(allowedFields, m));
        view.dimensions().forEach(d -> addNames(allowedFields, d));
        List<Map<String, Object>> filters = new ArrayList<>();
        for (DatasetFilter filter : request.filters()) {
            if (!allowedFields.contains(filter.field())) {
                throw new DatasetReadException(DatasetReadErrorCode.UNSUPPORTED_FILTER,
                        "指标视图不支持字段筛选: " + filter.field());
            }
            filters.add(Map.of("field", filter.field(), "operator", filter.operator(), "value", filter.value()));
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
        body.put("queryResultType", "DATA");
        return Map.copyOf(body);
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
