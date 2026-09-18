package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.dto.AloudataAnalysisViewDetail;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasetMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataAnalysisViewQueryCompiler;
import vip.mate.dataagent.service.AloudataAnalysisViewService;

import java.util.*;

/** 将 Aloudata 已有指标视图转换为统一 DatasetSourceAdapter。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AloudataAnalysisViewAdapter implements DatasetSourceAdapter {
    private static final int MAX_PAGE_SIZE = 10_000;

    private final DatasetMapper datasetMapper;
    private final DatasourceMapper datasourceMapper;
    private final AloudataAnalysisViewService viewService;
    private final AloudataAnalysisViewQueryCompiler queryCompiler;
    private final AloudataConfigHelper configHelper;
    private final AloudataApiClient apiClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(DatasetSourceType sourceType) {
        return sourceType == DatasetSourceType.ALOUDATA_ANALYSIS_VIEW;
    }

    @Override
    public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId) {
        DatasetEntity dataset = requireDataset(context, datasetId);
        String viewName = viewName(dataset);
        AloudataAnalysisViewDetail view = viewDetail(dataset.getDatasourceId(), viewName);
        List<DatasetColumn> columns = new ArrayList<>();
        view.dimensions().forEach(d -> columns.add(column(d, "dimension")));
        view.metrics().forEach(m -> columns.add(column(m, "measure")));
        return new DatasetInputDescriptor(datasetId, viewName, DatasetSourceType.ALOUDATA_ANALYSIS_VIEW,
                columns, dataset.getRowCount(), Map.of("datasourceId", dataset.getDatasourceId(), "viewName", viewName), null);
    }

    @Override
    public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        DatasetEntity dataset = requireDataset(context, request.datasetId());
        String viewName = viewName(dataset);
        DatasourceEntity datasource = datasourceMapper.selectById(dataset.getDatasourceId());
        if (datasource == null) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Aloudata 数据源不存在");
        }
        int limit = request.limit() == null ? 100 : Math.min(request.limit(), MAX_PAGE_SIZE);
        Map<String, Object> params;
        PushdownReport report;
        String endpoint;
        if (request.filters().isEmpty()) {
            endpoint = "analysis_view_query_data";
            params = new LinkedHashMap<>();
            params.put("viewName", viewName);
            params.put("pageSize", limit);
            // DatasetReadRequest.offset is a row offset; Aloudata's result
            // endpoint expects a zero-based page index.
            int offset = request.offset() == null ? 0 : request.offset();
            if (offset % limit != 0) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,
                        "Aloudata 分页仅支持按 pageSize 对齐的 offset");
            }
            params.put("pageIndex", offset / limit);
            params.put("queryResultType", "DATA");
            report = new PushdownReport(List.of(), List.of(), true, true, null);
        } else {
            endpoint = "metrics_query";
            AloudataAnalysisViewDetail view = viewDetail(dataset.getDatasourceId(), viewName);
            params = new LinkedHashMap<>(queryCompiler.compile(view, request));
            report = new PushdownReport(request.filters(), List.of(), true, true, null);
        }
        try {
            ResponseEntity<Map> response = apiClient.callWithParams(endpoint, configHelper.parseConfig(datasource), params);
            Map<String, Object> body = response == null || response.getBody() == null
                    ? Map.of() : objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
            String code = string(body, "code");
            if ("SM_02_0038".equals(code)) {
                throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "当前账号无权访问该指标视图，请在 Aloudata 指标平台为该账号授予该视图的权限（VIEW_ACCESS_DENIED）");
            }
            if (Boolean.FALSE.equals(body.get("success"))) {
                throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,
                        Optional.ofNullable(string(body, "message", "errorMsg")).orElse("Aloudata 请求失败"));
            }
            List<Map<String, Object>> rows = rows(body);
            return new DatasetBatch(rows, null, rows.size(), true, report);
        } catch (DatasetReadException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_TIMEOUT, "Aloudata 请求超时", e);
        } catch (RestClientException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Aloudata 服务不可用", e);
        }
    }

    /** 草稿视图预览：沿用正式读取的 Aloudata 查询端点，但不依赖已落库 DatasetEntity。 */
    public DatasetBatch previewDraft(DatasetAccessContext context, Long datasourceId, String viewName, DatasetReadRequest request) {
        if (context == null || datasourceId == null || viewName == null || viewName.isBlank())
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "Aloudata 指标视图参数不完整");
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Aloudata 数据源不存在");
        int limit = request.limit() == null ? 100 : Math.min(request.limit(), MAX_PAGE_SIZE);
        Map<String, Object> params = new LinkedHashMap<>();
        String endpoint;
        PushdownReport report;
        if (request.filters().isEmpty()) {
            endpoint = "analysis_view_query_data";
            params.put("viewName", viewName); params.put("pageSize", limit); params.put("pageIndex", 0); params.put("queryResultType", "DATA");
            report = new PushdownReport(List.of(), List.of(), true, true, null);
        } else {
            endpoint = "metrics_query";
            AloudataAnalysisViewDetail view = viewDetail(datasourceId, viewName);
            params.putAll(queryCompiler.compile(view, request));
            report = new PushdownReport(request.filters(), List.of(), true, true, null);
        }
        try {
            ResponseEntity<Map> response = apiClient.callWithParams(endpoint, configHelper.parseConfig(datasource), params);
            Map<String, Object> body = response == null || response.getBody() == null ? Map.of() : objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
            String code = string(body, "code");
            if ("SM_02_0038".equals(code)) throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "当前账号无权访问该指标视图，请在 Aloudata 指标平台为该账号授予该视图的权限（VIEW_ACCESS_DENIED）");
            if (Boolean.FALSE.equals(body.get("success"))) throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,
                    Optional.ofNullable(string(body, "message", "errorMsg")).orElse("Aloudata 请求失败"));
            List<Map<String,Object>> rows = rows(body);
            return new DatasetBatch(rows, null, rows.size(), true, report);
        } catch (DatasetReadException e) { throw e; }
        catch (ResourceAccessException e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_TIMEOUT, "Aloudata 请求超时", e); }
        catch (RestClientException e) { throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Aloudata 服务不可用", e); }
    }

    private DatasetEntity requireDataset(DatasetAccessContext context, long datasetId) {
        if (context == null || !context.canRead(datasetId)) {
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        }
        DatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不存在: " + datasetId);
        }
        if (!DatasetSourceType.ALOUDATA_ANALYSIS_VIEW.name().equalsIgnoreCase(dataset.getSourceType())) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是 Aloudata 指标视图");
        }
        return dataset;
    }

    private String viewName(DatasetEntity dataset) {
        if (dataset.getSourceConfig() != null && !dataset.getSourceConfig().isBlank()) {
            try {
                Map<String, Object> config = objectMapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {});
                // 管理 API 固化的是 analysisViewId；历史数据可能保存 viewName。
                Object value = config.get("analysisViewId");
                if (value == null) value = config.get("viewName");
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
            } catch (Exception e) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "source_config 不是合法 JSON", e);
            }
        }
        if (dataset.getName() == null || dataset.getName().isBlank()) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "未配置指标视图名称");
        }
        return dataset.getName();
    }

    /**
     * 任务级 Runner 读取没有 Web 请求线程的 UserContext；数据集白名单已在
     * {@link #requireDataset(DatasetAccessContext, long)} 校验，因此这里允许通过
     * 同一数据源连接直接读取视图详情，避免把内部数据面错误地当成未初始化用户。
     */
    private AloudataAnalysisViewDetail viewDetail(Long datasourceId, String viewName) {
        try {
            return viewService.getByName(datasourceId, viewName);
        } catch (IllegalStateException e) {
            if (!String.valueOf(e.getMessage()).contains("用户上下文未初始化")) throw e;
            DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
            if (datasource == null) throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "Aloudata 数据源不存在");
            ResponseEntity<Map> response = apiClient.callWithParams("analysis_view_query_by_name",
                    configHelper.parseConfig(datasource), Map.of("viewName", viewName));
            Map<String, Object> body = response == null || response.getBody() == null
                    ? Map.of() : objectMapper.convertValue(response.getBody(), new TypeReference<>() {});
            Object raw = body.get("data");
            Map<String, Object> data = raw instanceof Map<?, ?> ? objectMapper.convertValue(raw, new TypeReference<>() {}) : body;
            return new AloudataAnalysisViewDetail(
                    text(data, "id"), text(data, "viewName", "name"), text(data, "displayName"), text(data, "description"),
                    list(data.get("metrics")), list(data.get("dimensions")), text(data, "timeConstraint"),
                    list(data.get("filters")), list(data.get("resultFilters")), list(data.get("orders")));
        }
    }

    private String text(Map<String, Object> data, String... keys) {
        for (String key : keys) if (data.get(key) != null && !String.valueOf(data.get(key)).isBlank()) return String.valueOf(data.get(key));
        return null;
    }

    private List<Map<String, Object>> list(Object value) {
        return value == null ? List.of() : objectMapper.convertValue(value, new TypeReference<>() {});
    }

    private DatasetColumn column(Map<String, Object> definition, String role) {
        String name = string(definition, "name", "metricName", "dimName", "code");
        String title = string(definition, "displayName", "metricDisplayName", "dimDisplayName", "name");
        return new DatasetColumn(name, title, Optional.ofNullable(string(definition, "dataType", "originDataType")).orElse("STRING"), true, role);
    }

    /**
     * 从 Aloudata 指标视图查询响应中抽取行数据。
     * 真实响应包络多样：行式（{@code data.rows} / {@code analysisView.rows}）、列式
     * （{@code data.columns}）或整包络即数组。为保证“添加数据集-指标视图”预览不
     * 因响应结构不符而抛 Jackson 反序列化异常，这里做容错抽取：
     * 1) 优先取 {@code data}（标准包络），其次 {@code analysisView}；
     * 2) 容器为数组直接用；为对象则先找显式行容器（rows/rowData/...），再试列式 columns；
     * 3) 任何情况下都不抛反序列化异常，抽取不到返回空列表并告警。
     */
    private List<Map<String, Object>> rows(Map<String, Object> body) {
        if (body == null) return List.of();
        Object container = body.get("data");
        if (!(container instanceof Map<?, ?>)) container = body.get("analysisView");
        if (!(container instanceof Map<?, ?>)) container = body;

        List<Map<String, Object>> result = extractRowsFromContainer(container);
        if (result != null) return result;

        // 兜底：直接在 body 任意层级找第一个 List<Map>
        for (Object v : body.values()) {
            if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                return objectMapper.convertValue(list, new TypeReference<>() {});
            }
        }
        log.warn("Aloudata 指标视图响应未解析到行数据，已返回空结果。响应 keys={}", body.keySet());
        return List.of();
    }

    private List<Map<String, Object>> extractRowsFromContainer(Object container) {
        return extractRows(container, 0);
    }

    /**
     * 递归抽取行数据。真实响应存在多层包络：指标视图结果查询为
     * {@code data.analysisView.columns}，指标数据查询为 {@code data.table.columns}
     * （官方文档示例写作 {@code data.table.columns}）。因此这里先下钻
     * {@code analysisView} / {@code table} 子容器，再匹配行式（rows/...）或列式（columns）。
     * 任一环抽取不到都返回 null 交上层兜底，绝不抛异常。
     */
    private List<Map<String, Object>> extractRows(Object container, int depth) {
        if (container == null || depth > 4) return null;
        if (container instanceof List<?> list) {
            return objectMapper.convertValue(list, new TypeReference<>() {});
        }
        if (container instanceof Map<?, ?> map) {
            // 0) 嵌套包络下钻：analysisView（指标视图结果）/ table（指标数据查询）
            for (String nested : List.of("analysisView", "table")) {
                Object child = map.get(nested);
                if (child instanceof Map<?, ?> || child instanceof List<?>) {
                    List<Map<String, Object>> inner = extractRows(child, depth + 1);
                    if (inner != null) return inner;
                }
            }
            // 1) 行式：显式行容器
            for (String key : List.of("rows", "rowData", "rowDatas", "rowDataList", "records", "list", "result", "items")) {
                Object candidate = map.get(key);
                if (candidate instanceof List<?> list && !list.isEmpty()) {
                    return objectMapper.convertValue(list, new TypeReference<>() {});
                }
            }
            // 2) 列式：columns = { colName: [ {value,flag,count}, ... ] }
            Object columns = map.get("columns");
            if (columns instanceof Map<?, ?> columnMap && !columnMap.isEmpty()) {
                return convertColumnar(columnMap);
            }
        }
        return null;
    }

    /** 列式数据 columns = { colName: [ {value,flag,count}, ... ] } → 行式 List<Map> */
    private List<Map<String, Object>> convertColumnar(Map<?, ?> columnMap) {
        int size = 0;
        for (Object v : columnMap.values()) {
            if (v instanceof List<?> list) size = Math.max(size, list.size());
        }
        if (size == 0) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : columnMap.entrySet()) {
                String col = String.valueOf(entry.getKey());
                Object cellList = entry.getValue();
                if (cellList instanceof List<?> list && i < list.size()) {
                    Object cell = list.get(i);
                    if (cell instanceof Map<?, ?> cellMap && cellMap.containsKey("value")) {
                        row.put(col, cellMap.get("value"));
                    } else {
                        row.put(col, cell);
                    }
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private String string(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }
}
