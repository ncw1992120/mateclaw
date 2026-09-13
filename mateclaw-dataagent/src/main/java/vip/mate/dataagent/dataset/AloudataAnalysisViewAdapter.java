package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
                throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "VIEW_ACCESS_DENIED");
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

    private List<Map<String, Object>> rows(Map<String, Object> body) {
        Object value = body.get("analysisView");
        if (value == null) value = body.get("data");
        if (value == null) return List.of();
        if (value instanceof Map<?, ?> map && map.get("rows") != null) value = map.get("rows");
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    private String string(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }
}
