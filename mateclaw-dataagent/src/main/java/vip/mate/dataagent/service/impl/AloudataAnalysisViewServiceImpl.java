package vip.mate.dataagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.dto.AloudataAnalysisViewField;
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
    public List<AloudataAnalysisViewField> listFields(Long datasourceId, String viewName) {
        if (viewName == null || viewName.isBlank()) {
            throw new BusinessException(400, "指标视图名称不能为空");
        }
        DatasourceEntity datasource = requireAloudataDatasource(datasourceId);
        // 视图详情只给出指标/维度名称数组（metrics/dimensions 为 List<String>），
        // 展示名与描述需分别从 metrics/batchDetail 与 dimension/list 补齐。
        Map<String, Object> body = call("analysis_view_query_by_name", datasource, Map.of("viewName", viewName));
        Map<String, Object> data = asMap(body.get("data"));
        if (data.isEmpty()) {
            data = body;
        }
        List<String> metricNames = stringList(data.get("metrics"));
        List<String> dimNames = stringList(data.get("dimensions"));

        List<AloudataAnalysisViewField> result = new ArrayList<>();
        if (!metricNames.isEmpty()) {
            Map<String, Object> detailBody = call("metric_batch_detail", datasource, Map.of("metricNames", metricNames));
            Map<String, Map<String, Object>> byName = indexBy(mapList(detailBody.get("data")), "metricName");
            for (String name : metricNames) {
                Map<String, Object> detail = byName.get(name);
                result.add(new AloudataAnalysisViewField(name,
                        detail == null ? null : stringValue(detail, "metricDisplayName", "displayName"),
                        detail == null ? null : stringValue(detail, "businessCaliber", "description"),
                        AloudataAnalysisViewField.ROLE_MEASURE));
            }
        }
        if (!dimNames.isEmpty()) {
            // 维度接口无按名批量查询，取一页全量后按视图用到的维度名过滤（pageSize 上限 1000）。
            Map<String, Object> dimBody = call("dimension_list", datasource,
                    Map.of("pager", Map.of("pageNumber", 1, "pageSize", 1000)));
            Map<String, Map<String, Object>> byName = indexBy(mapList(asMap(dimBody.get("data")).get("data")), "dimName");
            for (String name : dimNames) {
                Map<String, Object> detail = byName.get(name);
                result.add(new AloudataAnalysisViewField(name,
                        detail == null ? null : stringValue(detail, "dimDisplayName", "displayName"),
                        detail == null ? null : stringValue(detail, "dimDescription", "description"),
                        AloudataAnalysisViewField.ROLE_DIMENSION));
            }
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
        // 实测（Aloudata demo 租户）：queryByName 的 metrics/dimensions 是**字符串数组**
        // （如 ["AUM", "RJCYKSL1"]），展示名单独放在 data.displayNameMap（name → displayName）。
        // 统一规范化为对象数组，让 Adapter 的列描述、QueryCompiler 的字段名校验，以及前端
        // viewDisplayNameMap 兜底都能拿到展示名；同时兼容旧的对象数组形态。
        Map<String, Object> displayNameMap = asMap(data.get("displayNameMap"));
        return new AloudataAnalysisViewDetail(
                stringValue(data, "id"), stringValue(data, "viewName"),
                stringValue(data, "displayName"), stringValue(data, "description"),
                definitionList(data.get("metrics"), displayNameMap),
                definitionList(data.get("dimensions"), displayNameMap),
                stringValue(data, "timeConstraint"), mapList(data.get("filters")),
                mapList(data.get("resultFilters")), mapList(data.get("orders")));
    }

    /**
     * 把视图定义里的 metrics / dimensions 规范化为「字段定义 Map 列表」。
     * <p>
     * 真实 {@code analysisview/queryByName} 返回名称字符串数组，展示名单独放在
     * {@code displayNameMap}；而历史夹具与部分环境返回对象数组（{@code name}/{@code displayName}）。
     * 两种形态都接受：字符串元素补 {@code name} 并从 displayNameMap 取 {@code displayName}，
     * 对象元素原样保留。
     */
    private List<Map<String, Object>> definitionList(Object value, Map<String, Object> displayNameMap) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {}));
                continue;
            }
            if (item == null) continue;
            String name = String.valueOf(item).trim();
            if (name.isEmpty()) continue;
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("name", name);
            Object display = displayNameMap.get(name);
            if (display != null && !String.valueOf(display).isBlank()) {
                definition.put("displayName", String.valueOf(display));
            }
            out.add(definition);
        }
        return List.copyOf(out);
    }

    private Map<String, Object> call(String endpoint, DatasourceEntity datasource, Map<String, Object> params) {
        ResponseEntity<Map> response = apiClient.callWithParams(endpoint, configHelper.parseConfig(datasource), params);
        if (response == null || response.getBody() == null) {
            throw new BusinessException(502, "Aloudata 返回空响应");
        }
        Map<String, Object> body = objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
        String code = stringValue(body, "code");
        if ("SM_02_0038".equals(code)) {
            throw new BusinessException(403, "当前账号无权访问该指标视图，请在 Aloudata 指标平台为该账号授予该视图的权限（VIEW_ACCESS_DENIED）");
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

    /**
     * 把视图定义里的 {@code metrics}/{@code dimensions} 容错转为字段名列表。
     * <p>
     * 真实 {@code analysisview/queryByName} 返回**字符串数组**（实测 demo 租户：
     * {@code ["AUM", "RJCYKSL1"]}），直接用 {@link #mapList(Object)}（按 Map 转）会抛 Jackson 异常；
     * 但部分环境/历史夹具返回对象数组，此时 {@code String.valueOf(map)} 会产出
     * {@code "{name=...}"} 之类的脏串。故两种形态都归一为字段名。
     */
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            String name = null;
            if (item instanceof Map<?, ?> map) {
                for (String key : List.of("name", "metricName", "dimName", "code")) {
                    Object v = map.get(key);
                    if (v != null && !String.valueOf(v).isBlank()) {
                        name = String.valueOf(v).trim();
                        break;
                    }
                }
            } else if (item != null) {
                name = String.valueOf(item).trim();
            }
            if (name != null && !name.isEmpty()) out.add(name);
        }
        return List.copyOf(out);
    }

    /** 按指定键把列表索引为 name → 元素 Map，便于按名称补齐详情 */
    private Map<String, Map<String, Object>> indexBy(List<Map<String, Object>> items, String key) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> item : items) {
            String name = stringValue(item, key);
            if (name != null) index.put(name, item);
        }
        return index;
    }
}
