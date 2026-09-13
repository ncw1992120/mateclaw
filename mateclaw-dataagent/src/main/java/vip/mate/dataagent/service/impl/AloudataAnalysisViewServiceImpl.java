package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewSummary;
import vip.mate.dataagent.exception.BusinessException;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataAnalysisViewService;
import vip.mate.dataagent.service.DatasourceManageService;

import java.util.*;

/** 只读访问 Aloudata 已有指标视图目录和定义。 */
@Service
@RequiredArgsConstructor
public class AloudataAnalysisViewServiceImpl implements AloudataAnalysisViewService {
    private final DatasourceMapper datasourceMapper;
    private final DatasourceManageService datasourceManageService;
    private final AloudataConfigHelper configHelper;
    private final AloudataApiClient apiClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<AloudataAnalysisViewSummary> listTree(Long datasourceId) {
        DatasourceEntity datasource = requireAloudataDatasource(datasourceId);
        Map<String, Object> body = call("analysis_view_tree", datasource, Map.of());
        List<AloudataAnalysisViewSummary> result = new ArrayList<>();
        flattenTree(body, null, null, result);
        return List.copyOf(result);
    }

    @Override
    public AloudataAnalysisViewDetail getByName(Long datasourceId, String viewName) {
        if (viewName == null || viewName.isBlank()) {
            throw new BusinessException(400, "指标视图名称不能为空");
        }
        DatasourceEntity datasource = requireAloudataDatasource(datasourceId);
        Map<String, Object> body = call("analysis_view_query_by_name", datasource, Map.of("viewName", viewName));
        Map<String, Object> data = asMap(body.get("data"));
        if (data.isEmpty()) {
            data = body;
        }
        return new AloudataAnalysisViewDetail(
                stringValue(data, "id"), stringValue(data, "viewName"),
                stringValue(data, "displayName"), stringValue(data, "description"),
                mapList(data.get("metrics")), mapList(data.get("dimensions")),
                stringValue(data, "timeConstraint"), mapList(data.get("filters")),
                mapList(data.get("resultFilters")), mapList(data.get("orders")));
    }

    private Map<String, Object> call(String endpoint, DatasourceEntity datasource, Map<String, Object> params) {
        ResponseEntity<Map> response = apiClient.callWithParams(endpoint, configHelper.parseConfig(datasource), params);
        if (response == null || response.getBody() == null) {
            throw new BusinessException(502, "Aloudata 返回空响应");
        }
        Map<String, Object> body = objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
        String code = stringValue(body, "code");
        if ("SM_02_0038".equals(code)) {
            throw new BusinessException(403, "VIEW_ACCESS_DENIED");
        }
        Object success = body.get("success");
        if (Boolean.FALSE.equals(success) || (code != null && !code.isBlank() && !"0".equals(code) && !"200".equals(code))) {
            String message = stringValue(body, "message", "errorMsg");
            throw new BusinessException(502, message == null ? "Aloudata 请求失败" : message);
        }
        return body;
    }

    private DatasourceEntity requireAloudataDatasource(Long id) {
        // 复用现有数据源服务的 workspace/owner/grant 校验，避免只依赖 Controller 注解。
        datasourceManageService.getDatasource(id);
        DatasourceEntity datasource = datasourceMapper.selectById(id);
        if (datasource == null) {
            throw new BusinessException(404, "数据源不存在: " + id);
        }
        String type = datasource.getSourceType() == null ? "" : datasource.getSourceType().toLowerCase(Locale.ROOT);
        if (!type.contains("aloudata") && !type.contains("anymetrics")) {
            throw new BusinessException(400, "数据源不是 Aloudata 类型");
        }
        return datasource;
    }

    private void flattenTree(Map<String, Object> node, String categoryId, String categoryName,
                             List<AloudataAnalysisViewSummary> out) {
        String currentId = stringValue(node, "categoryId", "id", categoryId);
        String currentName = stringValue(node, "categoryName", "name", categoryName);
        for (Map<String, Object> view : mapList(node.get("analysisViewList"))) {
            out.add(new AloudataAnalysisViewSummary(stringValue(view, "id", "viewId"),
                    stringValue(view, "viewName", "name"),
                    stringValue(view, "displayName", "viewDisplayName", "name"), currentId, currentName));
        }
        for (Map<String, Object> child : mapList(node.get("subCategory"))) {
            flattenTree(child, currentId, currentName, out);
        }
        for (Map<String, Object> child : mapList(node.get("data"))) {
            if (child.containsKey("subCategory") || child.containsKey("analysisViewList")) {
                flattenTree(child, currentId, currentName, out);
            }
        }
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (value == null) return List.of();
        return objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?>) return objectMapper.convertValue(value, new TypeReference<>() {});
        return Map.of();
    }

    private String stringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }
}
