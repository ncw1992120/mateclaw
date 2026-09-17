package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewItem;
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
    public List<AloudataAnalysisViewItem> listViews(Long datasourceId, String keyword, boolean onlyMine) {
        DatasourceEntity datasource = requireAloudataDatasource(datasourceId);
        // 实测：keyword 不传会返回 AM_00_0000（文档称「不传则查所有」，与实现不符），
        // 故空关键字时用单字符通配 "_" 查询全量。
        String kw = (keyword == null || keyword.isBlank()) ? "_" : keyword.trim();
        Map<String, Object> body = call("analysis_view_list", datasource,
                Map.of("keyword", kw, "pageNumber", 1, "pageSize", 200));
        Map<String, Object> data = asMap(body.get("data"));
        // owner 为 Aloudata UID；当前认证值（数据源级 auth-value）即「我」的 UID
        String me = configHelper.parseConfig(datasource).getAuthValue();
        List<AloudataAnalysisViewItem> result = new ArrayList<>();
        for (Map<String, Object> item : mapList(data.get("data"))) {
            Map<String, Object> basic = asMap(item.get("basicAttributes"));
            String owner = stringValue(basic, "owner");
            boolean mine = me != null && !me.isBlank() && me.equals(owner);
            if (onlyMine && !mine) continue;
            String displayName = stringValue(item, "displayName", "viewDisplayName");
            if (displayName == null) displayName = stringValue(basic, "viewDisplayName");
            result.add(new AloudataAnalysisViewItem(
                    stringValue(item, "id", "viewId"),
                    stringValue(item, "viewName", "name"),
                    displayName,
                    stringValue(item, "description"),
                    owner, mine));
        }
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
        // Aloudata analysis_view_tree 真实响应：树根列表位于 data.analysisViewRoots，
        // 每个根节点含 categoryId/categoryName/analysisViewList/subCategory。
        for (Map<String, Object> child : mapList(node.get("analysisViewRoots"))) {
            flattenTree(child, currentId, currentName, out);
        }
        for (Map<String, Object> child : mapList(node.get("data"))) {
            if (child.containsKey("subCategory") || child.containsKey("analysisViewList")
                    || child.containsKey("analysisViewRoots")) {
                flattenTree(child, currentId, currentName, out);
            }
        }
    }

    /**
     * 容错地把 Aloudata 响应字段转换为节点列表：数组直接转换；单个对象包装为
     * 单元素列表（真实 analysis_view_tree 的 data 即树根对象而非数组）；其余返回空列表。
     * 任何情况下不抛 Jackson 反序列化异常，避免「指标视图目录」接口 500。
     */
    private List<Map<String, Object>> mapList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Map<?, ?>) {
            Map<String, Object> single = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
            return List.of(single);
        }
        if (value instanceof List<?>) {
            return objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
        }
        return List.of();
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
