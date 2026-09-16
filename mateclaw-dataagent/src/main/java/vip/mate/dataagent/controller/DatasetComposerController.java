package vip.mate.dataagent.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.dataagent.auth.annotation.RequireWorkspaceRole;
import vip.mate.dataagent.auth.service.WorkspaceGuard;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dataset.DatasetFilter;
import vip.mate.dataagent.dataset.DatasetBatch;
import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.jdbc.SqlValidationService;
import vip.mate.dataagent.dto.DatasetCreateRequest;
import vip.mate.dataagent.dto.DatasetSourceDefinition;
import vip.mate.dataagent.dto.DatasetVO;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.auth.crypto.AesPasswordCryptor;
import vip.mate.dataagent.util.JdbcUtils;
import vip.mate.dataagent.dataset.http.HttpApiRequestPolicy;
import vip.mate.dataagent.dataset.http.HttpApiDatasetAdapter;
import vip.mate.dataagent.dataset.http.HttpApiDatasetDefinition;
import vip.mate.dataagent.dataset.file.FileDatasetAdapter;
import vip.mate.dataagent.dataset.file.StoredFileRef;
import vip.mate.dataagent.dataset.AloudataAnalysisViewAdapter;
import vip.mate.dataagent.dto.AloudataMetricQueryRequest;
import vip.mate.dataagent.dto.AloudataMetricQueryResponse;
import vip.mate.dataagent.service.AloudataService;
import vip.mate.dataagent.service.DatasetManageService;

import java.util.*;
import java.sql.*;
import java.net.URI;

/** 原型“添加数据集”使用的来源草稿接口。草稿预览不落库，确定后才创建可复用 Dataset。 */
@RestController
@RequestMapping("/v1/dataset-composer")
@RequiredArgsConstructor
@Tag(name = "数据集编排")
public class DatasetComposerController {
    private final DatasetManageService datasets;
    private final SqlValidationService sqlValidation;
    private final ObjectMapper mapper;
    private final WorkspaceGuard workspaceGuard;
    private final DatasourceMapper datasourceMapper;
    private final HttpApiRequestPolicy httpPolicy;
    private final AloudataService aloudataService;
    private final HttpApiDatasetAdapter httpApiAdapter;
    private final FileDatasetAdapter fileDatasetAdapter;
    private final AloudataAnalysisViewAdapter aloudataViewAdapter;

    @PostMapping("/drafts/preview")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_VIEWER)
    @Operation(summary = "预览数据集来源草稿")
    public R<Map<String, Object>> preview(@RequestBody DraftRequest request) {
        validate(request, true);
        if ("JDBC_SQL".equalsIgnoreCase(request.sourceType)) return R.ok(previewJdbc(request));
        if ("ALOUDATA_METRICS".equalsIgnoreCase(request.sourceType)) return R.ok(previewAloudataMetrics(request));
        if ("HTTP_API".equalsIgnoreCase(request.sourceType)) return R.ok(previewHttpApi(request));
        if ("FILE".equalsIgnoreCase(request.sourceType)) return R.ok(previewFile(request));
        if ("ALOUDATA_ANALYSIS_VIEW".equalsIgnoreCase(request.sourceType)) return R.ok(previewAloudataView(request));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", List.of());
        result.put("schema", List.of());
        result.put("rowCount", 0);
        result.put("pushdownReport", Map.of("pushedFilters", request.filters == null ? List.of() : request.filters, "residualFilters", List.of()));
        result.put("executionId", "draft-" + UUID.randomUUID());
        return R.ok(result);
    }

    private Map<String, Object> previewAloudataMetrics(DraftRequest request) {
        Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
        AloudataMetricQueryRequest query = new AloudataMetricQueryRequest();
        query.setMetrics(strings(config.get("metrics"))); query.setDimensions(strings(config.get("dimensions")));
        query.setFilters((request.filters == null ? List.<Map<String, Object>>of() : request.filters).stream().map(f -> "[" + f.get("field") + "] " + String.valueOf(f.getOrDefault("operator", "eq")).toUpperCase(Locale.ROOT) + " (\"" + String.valueOf(f.get("value")).replace("\"", "\\\"") + "\")").toList());
        query.setLimit(Math.min(request.limit == null ? 20 : request.limit, 100)); query.setOffset(0);
        AloudataMetricQueryResponse response = aloudataService.queryMetrics(longId(request.datasourceId), query);
        List<Map<String, Object>> rows = response == null || response.getData() == null || response.getData().getRows() == null ? List.of() : response.getData().getRows();
        return Map.of("rows", rows, "schema", rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet()), "rowCount", rows.size(), "pushdownReport", Map.of("pushedFilters", request.filters == null ? List.of() : request.filters, "residualFilters", List.of()), "executionId", "draft-" + UUID.randomUUID());
    }

    private Map<String, Object> previewHttpApi(DraftRequest request) {
        Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
        String host = String.valueOf(config.getOrDefault("host", "")).trim().replaceAll("/$", "");
        String path = String.valueOf(config.getOrDefault("path", "")).trim();
        if (host.isBlank() || path.isBlank()) throw new IllegalArgumentException("HTTP Host 和请求路径不能为空");
        String endpoint = (host.matches("https?://.*") ? host : "https://" + host) + (path.startsWith("/") ? path : "/" + path);
        URI uri = URI.create(endpoint);
        DatasourceEntity datasource = datasourceMapper.selectById(longId(request.datasourceId));
        if (datasource == null || !Objects.equals(datasource.getWorkspaceId(), workspaceGuard.currentWorkspaceId())
                || (!Boolean.TRUE.equals(datasource.getMetaShared()) && !Objects.equals(datasource.getOwnerId(), workspaceGuard.currentUserId()))) {
            throw new IllegalArgumentException("无权访问该接口数据源");
        }
        Map<String, Object> datasourceConfig = parseObject(datasource.getConnectionParams());
        List<String> hosts = strings(datasourceConfig.get("allowedHosts"));
        if (hosts.isEmpty() && datasource.getHost() != null) hosts = List.of(datasource.getHost());
        httpPolicy.validate(uri, hosts);
        httpPolicy.validateHeaders(parseStringMap(config.get("headers")));
        List<String> query = strings(config.get("allowedQueryParams"));
        List<String> body = strings(config.get("allowedBodyParams"));
        HttpApiDatasetDefinition definition = new HttpApiDatasetDefinition(uri,
                String.valueOf(config.getOrDefault("method", "GET")), query, body,
                String.valueOf(config.getOrDefault("resultPath", "$.data")), "none", null, null,
                "GET".equalsIgnoreCase(String.valueOf(config.getOrDefault("method", "GET"))), Map.of());
        DatasetBatch batch = httpApiAdapter.readDraft(new vip.mate.dataagent.dataset.DatasetReadRequest(
                0L, "draft", List.of(), toFilters(request.filters), Math.min(request.limit == null ? 20 : request.limit, 100), 0, Map.of()), definition, hosts);
        List<Map<String, Object>> rows = batch.rows() == null ? List.of() : batch.rows();
        return Map.of("rows", rows, "schema", rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet()),
                "rowCount", rows.size(), "pushdownReport", Map.of("pushedFilters", request.filters == null ? List.of() : request.filters,
                        "residualFilters", List.of()), "executionId", "draft-" + UUID.randomUUID());
    }

    private Map<String, Object> previewFile(DraftRequest request) {
        Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
        Object rawRef = config.get("fileRef");
        if (rawRef == null) throw new IllegalArgumentException("fileRef is required; upload the file before preview");
        StoredFileRef stored;
        try { stored = mapper.convertValue(rawRef, StoredFileRef.class); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("fileRef is invalid", e); }
        DatasetAccessContext context = new DatasetAccessContext(workspaceGuard.currentWorkspaceId(), workspaceGuard.currentUserId(),
                "draft-file-" + UUID.randomUUID(), Set.of());
        DatasetBatch batch = fileDatasetAdapter.previewDraft(context, stored, String.valueOf(config.getOrDefault("format", stored.format())),
                Math.min(request.limit == null ? 20 : request.limit, 100));
        List<Map<String, Object>> rows = batch.rows() == null ? List.of() : batch.rows();
        return Map.of("rows", rows, "schema", rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet()),
                "rowCount", rows.size(), "pushdownReport", Map.of("pushedFilters", List.of(), "residualFilters", request.filters == null ? List.of() : request.filters),
                "executionId", "draft-" + UUID.randomUUID());
    }

    private Map<String, Object> previewAloudataView(DraftRequest request) {
        Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
        String view = String.valueOf(config.getOrDefault("analysisViewId", "")).trim();
        if (view.isBlank()) throw new IllegalArgumentException("analysisViewId is required");
        DatasourceEntity datasource = datasourceMapper.selectById(longId(request.datasourceId));
        if (datasource == null || !Objects.equals(datasource.getWorkspaceId(), workspaceGuard.currentWorkspaceId())
                || (!Boolean.TRUE.equals(datasource.getMetaShared()) && !Objects.equals(datasource.getOwnerId(), workspaceGuard.currentUserId()))) {
            throw new IllegalArgumentException("无权访问该 Aloudata 数据源");
        }
        DatasetAccessContext context = new DatasetAccessContext(workspaceGuard.currentWorkspaceId(), workspaceGuard.currentUserId(),
                "draft-view-" + UUID.randomUUID(), Set.of());
        DatasetBatch batch = aloudataViewAdapter.previewDraft(context, longId(request.datasourceId), view,
                new vip.mate.dataagent.dataset.DatasetReadRequest(0L, "draft", List.of(), toFilters(request.filters),
                        Math.min(request.limit == null ? 20 : request.limit, 100), 0, Map.of()));
        List<Map<String,Object>> rows = batch.rows() == null ? List.of() : batch.rows();
        return Map.of("rows", rows, "schema", rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet()),
                "rowCount", rows.size(), "pushdownReport", Map.of("pushedFilters", request.filters == null ? List.of() : request.filters,
                        "residualFilters", List.of()), "executionId", "draft-" + UUID.randomUUID());
    }

    @PostMapping("/api-definitions")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "登记接口数据集定义")
    public R<Map<String, String>> registerApiDefinition(@RequestBody ApiDefinitionRequest request) {
        DatasourceEntity datasource = datasourceMapper.selectById(longId(request.datasourceId));
        if (datasource == null || !Objects.equals(datasource.getWorkspaceId(), workspaceGuard.currentWorkspaceId())
                || (!Boolean.TRUE.equals(datasource.getMetaShared()) && !Objects.equals(datasource.getOwnerId(), workspaceGuard.currentUserId()))) {
            throw new IllegalArgumentException("无权访问该接口数据源");
        }
        String endpoint = request.endpoint();
        if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("endpoint is required");
        URI uri = URI.create(endpoint);
        if (request.method() == null || request.method().isBlank()) throw new IllegalArgumentException("method is required");
        Map<String, Object> current = parseObject(datasource.getConnectionParams());
        List<String> hosts = strings(current.get("allowedHosts"));
        if (hosts.isEmpty() && datasource.getHost() != null) hosts = List.of(datasource.getHost());
        httpPolicy.validate(uri, hosts);
        httpPolicy.validateHeaders(request.headers());
        String id = request.id() == null || request.id().isBlank() ? "api-" + UUID.randomUUID() : request.id();
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("endpoint", endpoint); definition.put("method", request.method());
        definition.put("allowedQueryParams", request.allowedQueryParams()); definition.put("allowedBodyParams", request.allowedBodyParams());
        definition.put("resultPath", request.resultPath() == null ? "$.data" : request.resultPath());
        definition.put("idempotent", "GET".equalsIgnoreCase(request.method()));
        Map<String, Object> definitions = new LinkedHashMap<>(parseObject(current.get("apiDefinitions")));
        definitions.put(id, definition); current.put("apiDefinitions", definitions);
        try { datasource.setConnectionParams(mapper.writeValueAsString(current)); } catch (Exception e) { throw new IllegalArgumentException("接口定义序列化失败", e); }
        datasourceMapper.updateById(datasource);
        return R.ok(Map.of("apiDefinitionId", id));
    }

    /** 草稿 JDBC 预览直接使用受控连接执行，不写入 DatasetEntity；只返回受限行和字段元数据。 */
    private Map<String, Object> previewJdbc(DraftRequest request) {
        Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
        DatasourceEntity datasource = datasourceMapper.selectById(longId(request.datasourceId));
        if (datasource == null) throw new IllegalArgumentException("JDBC 数据源不存在");
        if (!Objects.equals(datasource.getWorkspaceId(), workspaceGuard.currentWorkspaceId())
                || (!Boolean.TRUE.equals(datasource.getMetaShared())
                && !Objects.equals(datasource.getOwnerId(), workspaceGuard.currentUserId()))) {
            throw new IllegalArgumentException("无权访问该 JDBC 数据源");
        }
        var compiled = sqlValidation.compile(String.valueOf(config.getOrDefault("sql", "")), List.of(),
                toFilters(request.filters), Math.min(request.limit == null ? 20 : request.limit, 100), 0);
        try (Connection connection = DriverManager.getConnection(JdbcUtils.buildJdbcUrl(datasource), datasource.getUsername(), AesPasswordCryptor.decrypt(datasource.getPassword()));
             PreparedStatement statement = connection.prepareStatement(compiled.sql())) {
            statement.setQueryTimeout(30);
            for (int i = 0; i < compiled.parameters().size(); i++) statement.setObject(i + 1, compiled.parameters().get(i));
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> schema = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) schema.add(md.getColumnLabel(i));
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next() && rows.size() < 100) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) row.put(schema.get(i - 1), rs.getObject(i));
                    rows.add(row);
                }
                return Map.of("rows", rows, "schema", schema, "rowCount", rows.size(), "pushdownReport",
                        Map.of("pushedFilters", request.filters == null ? List.of() : request.filters, "residualFilters", List.of(), "safe", true),
                        "executionId", "draft-" + UUID.randomUUID());
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("JDBC 草稿预览失败", e);
        }
    }

    @PostMapping("/drafts/confirm")
    @RequireWorkspaceRole(DataAgentConstants.WORKSPACE_ROLE_MEMBER)
    @Operation(summary = "确认数据集来源草稿")
    public R<Map<String, Object>> confirm(@RequestBody ConfirmRequest request) {
        validate(request, false);
        DatasetCreateRequest create = new DatasetCreateRequest();
        create.setName(request.name);
        create.setDescription(request.description);
        create.setDatasourceId(request.getDatasourceId());
        create.setSourceDefinition(toDefinition(request));
        DatasetVO dataset = datasets.createDataset(create);
        DatasetAccessContext context = new DatasetAccessContext(workspaceGuard.currentWorkspaceId(), workspaceGuard.currentUserId(), "composer", Set.of(dataset.getId()));
        return R.ok(Map.of("datasetId", String.valueOf(dataset.getId()), "descriptor", datasets.getInputDescriptor(context, dataset.getId(), request.name)));
    }

    private DatasetSourceDefinition toDefinition(DraftRequest request) {
        String type = request.sourceType.toUpperCase(Locale.ROOT);
        Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
        return switch (type) {
            case "JDBC_SQL" -> new DatasetSourceDefinition.JdbcSqlDefinition(longId(request.datasourceId), String.valueOf(config.getOrDefault("sql", "")));
            // 产品入口不会直接创建“未选表”的 JDBC_TABLE；已有表数据集走 datasetId 选择。
            case "JDBC_TABLE" -> throw new IllegalArgumentException("JDBC table source requires an existing dataset or tableIds");
            case "ALOUDATA_ANALYSIS_VIEW" -> new DatasetSourceDefinition.AloudataViewDefinition(longId(request.datasourceId), String.valueOf(config.getOrDefault("analysisViewId", "")));
            case "ALOUDATA_METRICS" -> new DatasetSourceDefinition.AloudataMetricsDefinition(longId(request.datasourceId), strings(config.get("metrics")), strings(config.get("dimensions")));
            case "HTTP_API" -> new DatasetSourceDefinition.HttpApiDefinition(longId(request.datasourceId), String.valueOf(config.getOrDefault("apiDefinitionId", "")));
            case "FILE" -> new DatasetSourceDefinition.FileDefinition(String.valueOf(config.getOrDefault("objectId", "")), String.valueOf(config.getOrDefault("format", "CSV")), 1);
            default -> throw new IllegalArgumentException("unsupported dataset source type: " + request.sourceType);
        };
    }

    private void validate(DraftRequest request, boolean preview) {
        if (request == null || request.sourceType == null || request.sourceType.isBlank()) throw new IllegalArgumentException("sourceType is required");
        if ("JDBC_SQL".equalsIgnoreCase(request.sourceType)) {
            Map<String, Object> config = request.sourceConfig == null ? Map.of() : request.sourceConfig;
            String sql = String.valueOf(config.getOrDefault("sql", ""));
            // AST 校验先于执行，避免注释、字符串中的 WHERE 绕过只读和单语句检查。
            sqlValidation.compile(sql, List.of(), toFilters(request.filters), Math.min(request.limit == null ? 20 : request.limit, 1000), 0);
            if (preview && !containsWhere(sql)) throw new IllegalArgumentException("JDBC SQL 预览必须包含 WHERE 条件");
        }
        if ("FILE".equalsIgnoreCase(request.sourceType) && (request.sourceConfig == null || request.sourceConfig.get("objectId") == null)) throw new IllegalArgumentException("file objectId is required");
    }

    private boolean containsWhere(String sql) {
        String normalized = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "");
        return normalized.toLowerCase(Locale.ROOT).matches("(?s).*\\bwhere\\b.*");
    }

    private List<DatasetFilter> toFilters(List<Map<String, Object>> filters) {
        if (filters == null) return List.of();
        return mapper.convertValue(filters, new TypeReference<>() {});
    }

    private long longId(String value) { try { return Long.parseLong(value); } catch (Exception e) { throw new IllegalArgumentException("datasourceId is required"); } }
    private List<String> strings(Object value) { return value == null ? List.of() : mapper.convertValue(value, new TypeReference<>() {}); }
    private Map<String, Object> parseObject(Object value) { if (value == null) return new LinkedHashMap<>(); if (value instanceof Map<?, ?> map) return mapper.convertValue(map, new TypeReference<>() {}); try { return mapper.readValue(String.valueOf(value), new TypeReference<>() {}); } catch (Exception e) { return new LinkedHashMap<>(); } }
    private Map<String, String> parseStringMap(Object value) { Map<String, Object> raw = parseObject(value); Map<String, String> result = new LinkedHashMap<>(); raw.forEach((k, v) -> result.put(k, String.valueOf(v))); return result; }

    @Data
    public static class DraftRequest {
        private String sourceType;
        private String datasourceId;
        private Map<String, Object> sourceConfig;
        private List<Map<String, Object>> filters;
        private Integer limit;
    }
    @Data
    public static class ConfirmRequest extends DraftRequest { private String name; private String description; }

    @Data
    public static class ApiDefinitionRequest {
        private String datasourceId;
        private String id;
        private String endpoint;
        private String method = "GET";
        private List<String> allowedQueryParams = List.of();
        private List<String> allowedBodyParams = List.of();
        private String resultPath = "$.data";
        private Map<String, String> headers = Map.of();
        public String datasourceId() { return datasourceId; } public String id() { return id; }
        public String endpoint() { return endpoint; } public String method() { return method; }
        public List<String> allowedQueryParams() { return allowedQueryParams == null ? List.of() : allowedQueryParams; }
        public List<String> allowedBodyParams() { return allowedBodyParams == null ? List.of() : allowedBodyParams; }
        public String resultPath() { return resultPath; } public Map<String, String> headers() { return headers == null ? Map.of() : headers; }
    }
}
