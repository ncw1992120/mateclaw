package vip.mate.dataagent.dataset.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import vip.mate.dataagent.dataset.*;
import vip.mate.dataagent.model.DatasetEntity;
import vip.mate.dataagent.repository.DatasetMapper;

import java.net.URI;
import java.util.*;

/** 仅按已登记定义访问 HTTP API，Runner 不可控制 URL、Header 或凭据。 */
@Component
public class HttpApiDatasetAdapter implements DatasetSourceAdapter {
    private static final int MAX_PAGE_SIZE = 10_000;
    private static final int MAX_PAGE_NUMBER = 1_000;
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_RESULT_ROWS = 10_000;

    private final DatasetMapper datasetMapper;
    private final HttpApiRequestPolicy policy;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public HttpApiDatasetAdapter(DatasetMapper datasetMapper, HttpApiRequestPolicy policy,
                                 ObjectMapper objectMapper) {
        this(datasetMapper, policy, objectMapper, defaultRestTemplate());
    }

    public HttpApiDatasetAdapter(DatasetMapper datasetMapper, HttpApiRequestPolicy policy,
                                 ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.datasetMapper = datasetMapper;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(DatasetSourceType sourceType) {
        return sourceType == DatasetSourceType.HTTP_API;
    }

    @Override
    public DatasetInputDescriptor describe(DatasetAccessContext context, long datasetId) {
        DatasetEntity dataset = requireDataset(context, datasetId);
        HttpApiDatasetDefinition definition = definition(dataset);
        List<DatasetColumn> columns = schema(dataset);
        return new DatasetInputDescriptor(datasetId, inputName(dataset), DatasetSourceType.HTTP_API,
                columns, dataset.getRowCount(), Map.of("endpoint", definition.endpoint().getPath()), null);
    }

    @Override
    public DatasetBatch read(DatasetAccessContext context, DatasetReadRequest request) {
        DatasetEntity dataset = requireDataset(context, request.datasetId());
        HttpApiDatasetDefinition definition = definition(dataset);
        return readResolved(request, definition, allowedHosts(dataset));
    }

    /** 草稿预览使用同一套安全策略，但不要求先创建 DatasetEntity。 */
    public DatasetBatch readDraft(DatasetReadRequest request, HttpApiDatasetDefinition definition, List<String> allowedHosts) {
        return readResolved(request, definition, allowedHosts == null ? List.of() : allowedHosts);
    }

    private DatasetBatch readResolved(DatasetReadRequest request, HttpApiDatasetDefinition definition, List<String> hosts) {
        try {
            policy.validate(definition.endpoint(), hosts);
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, e.getMessage(), e);
        }
        // HTTP 源端不支持排序下推：orders 非空时改取有界全量，本地排序后切片（禁止丢语义）
        boolean residualSort = request.orders() != null && !request.orders().isEmpty();
        DatasetReadRequest effectiveRequest = residualSort
                ? new DatasetReadRequest(request.datasetId(), request.inputName(), request.columns(),
                        request.filters(), List.of(), MAX_RESULT_ROWS, 0, request.parameters(),
                        request.requestTotalCount())
                : request;
        Map<String, Object> mapped;
        try {
            mapped = policy.mapFilters(definition, effectiveRequest.filters());
        } catch (IllegalArgumentException e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, e.getMessage(), e);
        }
        URI uri = buildUri(definition, mapped, effectiveRequest);
        HttpEntity<?> entity = new HttpEntity<>(buildBody(definition, mapped, effectiveRequest), new HttpHeaders());
        try {
            ResponseEntity<String> response = exchangeWithRetry(uri, definition, entity, hosts);
            if (response.getStatusCode().is3xxRedirection()) {
                throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE,
                        "HTTP API 重定向被禁止");
            }
            String raw = response.getBody() == null ? "" : response.getBody();
            if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "HTTP API 响应超过大小限制");
            }
            Map<String, Object> json = objectMapper.readValue(raw.isBlank() ? "{}" : raw, new TypeReference<>() {});
            List<Map<String, Object>> rows = extractRows(json, definition.resultPath());
            PushdownReport report;
            if (residualSort) {
                ResidualRowOperations.sort(rows, request.orders());
                rows = new ArrayList<>(ResidualRowOperations.paginate(rows, request.limit(), request.offset()));
                report = new PushdownReport(request.filters(), List.of(), List.of(), true, false, false, null);
            } else {
                report = new PushdownReport(request.filters(), List.of(), List.of(), true, true, false, null);
            }
            return new DatasetBatch(rows, null, rows.size(), true, report);
        } catch (DatasetReadException e) {
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "HTTP API 限流", e);
        } catch (HttpStatusCodeException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "HTTP API 请求失败: " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new DatasetReadException(DatasetReadErrorCode.SOURCE_TIMEOUT, "HTTP API 请求超时或不可达", e);
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "HTTP API 响应格式无效", e);
        }
    }

    RestTemplate restTemplate() { return restTemplate; }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    private ResponseEntity<String> exchangeWithRetry(URI uri, HttpApiDatasetDefinition definition, HttpEntity<?> entity,
                                                     List<String> allowedHosts) {
        int maxAttempts = definition.idempotent() ? 2 : 1;
        HttpMethod method = HttpMethod.valueOf(definition.method().toUpperCase(Locale.ROOT));
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // DNS can change after the initial definition validation. Re-check immediately
                // before every network attempt so a rebinding to a private address fails closed.
                policy.validate(uri, allowedHosts);
                return restTemplate.exchange(uri, method, entity, String.class);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (attempt < maxAttempts && (status == 429 || status >= 500)) continue;
                if (status == 429) throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "HTTP API 限流", e);
                throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "HTTP API 请求失败: " + status, e);
            } catch (RestClientException e) {
                throw new DatasetReadException(DatasetReadErrorCode.SOURCE_TIMEOUT, "HTTP API 请求超时或不可达", e);
            }
        }
        throw new DatasetReadException(DatasetReadErrorCode.SOURCE_UNAVAILABLE, "HTTP API 请求失败");
    }

    private DatasetEntity requireDataset(DatasetAccessContext context, long datasetId) {
        if (context == null || !context.canRead(datasetId))
            throw new DatasetReadException(DatasetReadErrorCode.ACCESS_DENIED, "无权读取数据集");
        DatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不存在: " + datasetId);
        if (!DatasetSourceType.HTTP_API.name().equalsIgnoreCase(dataset.getSourceType()))
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "数据集不是 HTTP/API 类型");
        return dataset;
    }

    private HttpApiDatasetDefinition definition(DatasetEntity dataset) {
        try {
            Map<String, Object> config = objectMapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {});
            URI endpoint = URI.create(String.valueOf(config.get("endpoint")));
            List<String> query = strings(config.get("allowedQueryParams"));
            List<String> body = strings(config.get("allowedBodyParams"));
            return new HttpApiDatasetDefinition(endpoint, String.valueOf(config.getOrDefault("method", "GET")), query, body,
                    String.valueOf(config.getOrDefault("resultPath", "$.data")), String.valueOf(config.getOrDefault("paginationMode", "none")),
                    (String) config.get("pageParam"), (String) config.get("sizeParam"),
                    Boolean.TRUE.equals(config.get("idempotent")), map(config.get("fixedBody")));
        } catch (Exception e) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "HTTP API 定义无效", e);
        }
    }

    private URI buildUri(HttpApiDatasetDefinition definition, Map<String, Object> mapped, DatasetReadRequest request) {
        if (definition.allowedQueryParams().isEmpty() && mapped.isEmpty() && "none".equalsIgnoreCase(definition.paginationMode())) return definition.endpoint();
        StringBuilder query = new StringBuilder(Optional.ofNullable(definition.endpoint().getQuery()).orElse(""));
        for (String key : definition.allowedQueryParams()) if (mapped.containsKey(key)) appendQuery(query, key, mapped.get(key));
        if ("page".equalsIgnoreCase(definition.paginationMode())) {
            int size = pageSize(request);
            int offset = request.offset() == null ? 0 : request.offset();
            if (offset % size != 0) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST,
                        "HTTP page 分页仅支持按 pageSize 对齐的 offset");
            }
            int page = offset / size + 1;
            if (page > MAX_PAGE_NUMBER) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "HTTP API 页数超过限制");
            appendQuery(query, Optional.ofNullable(definition.pageParam()).orElse("page"), page);
            appendQuery(query, Optional.ofNullable(definition.sizeParam()).orElse("size"), size);
        } else if ("offset".equalsIgnoreCase(definition.paginationMode())) {
            int size = pageSize(request);
            int offset = request.offset() == null ? 0 : request.offset();
            if (offset < 0 || offset / size >= MAX_PAGE_NUMBER) throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "HTTP API 页数超过限制");
            appendQuery(query, Optional.ofNullable(definition.pageParam()).orElse("offset"), offset);
            appendQuery(query, Optional.ofNullable(definition.sizeParam()).orElse("limit"), size);
        } else if ("cursor".equalsIgnoreCase(definition.paginationMode())) {
            Object cursor = request.parameters().get(definition.pageParam());
            if (cursor != null) appendQuery(query, Optional.ofNullable(definition.pageParam()).orElse("cursor"), cursor);
            appendQuery(query, Optional.ofNullable(definition.sizeParam()).orElse("limit"), pageSize(request));
        }
        return URI.create(definition.endpoint().toString().split("\\?", 2)[0] + (query.isEmpty() ? "" : "?" + query));
    }

    private int pageSize(DatasetReadRequest request) {
        int size = Math.min(request.limit() == null ? 100 : request.limit(), MAX_PAGE_SIZE);
        if (size <= 0 || request.offset() != null && request.offset() < 0)
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "HTTP API 分页参数无效");
        return size;
    }

    private void appendQuery(StringBuilder query, String key, Object value) {
        if (query.length() > 0) query.append('&');
        query.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8))
                .append('=').append(java.net.URLEncoder.encode(String.valueOf(value), java.nio.charset.StandardCharsets.UTF_8));
    }

    private Map<String, Object> buildBody(HttpApiDatasetDefinition definition, Map<String, Object> mapped, DatasetReadRequest request) {
        Map<String, Object> body = new LinkedHashMap<>(definition.fixedBody());
        for (String key : definition.allowedBodyParams()) if (mapped.containsKey(key)) body.put(key, mapped.get(key));
        return body.isEmpty() ? null : body;
    }

    private List<Map<String, Object>> extractRows(Map<String, Object> json, String path) {
        Object value = json;
        for (String part : path == null ? "$.data".split("\\.") : path.replaceFirst("^\\$\\.?", "").split("\\.")) {
            if (part.isBlank()) continue;
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(part)) {
                throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "HTTP API 结果路径不存在: " + path);
            }
            value = map.get(part);
        }
        if (value == null) return List.of();
        if (value instanceof Map<?, ?> map && map.get("rows") != null) value = map.get("rows");
        List<Map<String, Object>> rows = objectMapper.convertValue(value, new TypeReference<>() {});
        if (rows.size() > MAX_RESULT_ROWS) {
            throw new DatasetReadException(DatasetReadErrorCode.RESULT_LIMIT_EXCEEDED, "HTTP API 返回行数超过限制");
        }
        return rows;
    }

    private List<DatasetColumn> schema(DatasetEntity dataset) {
        try {
            Map<String, Object> config = objectMapper.readValue(dataset.getSourceConfig(), new TypeReference<>() {});
            List<Map<String, Object>> values = objectMapper.convertValue(config.getOrDefault("schema", List.of()), new TypeReference<>() {});
            return values.stream().map(v -> new DatasetColumn(String.valueOf(v.get("name")), String.valueOf(v.getOrDefault("title", v.get("name"))),
                    String.valueOf(v.getOrDefault("dataType", "STRING")), true, String.valueOf(v.getOrDefault("role", "dimension")))).toList();
        } catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "HTTP API Schema 无效", e); }
    }

    private List<String> allowedHosts(DatasetEntity dataset) {
        try { return strings(objectMapper.readValue(dataset.getSourceConfig(), new TypeReference<Map<String, Object>>() {}).get("allowedHosts")); }
        catch (Exception e) { throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "HTTP API allowlist 无效", e); }
    }

    private List<String> strings(Object value) { return value == null ? List.of() : objectMapper.convertValue(value, new TypeReference<>() {}); }
    private Map<String, Object> map(Object value) { return value == null ? Map.of() : objectMapper.convertValue(value, new TypeReference<>() {}); }
    private String inputName(DatasetEntity dataset) { return dataset.getName() == null ? "dataset-" + dataset.getId() : dataset.getName(); }
}
