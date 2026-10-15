package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit external probe for ALO-X01/ALO-X02.
 *
 * The class ends with {@code IT} so the normal unit-test phase does not call a
 * live tenant. Run it explicitly with {@code -Dtest=AloudataAnalysisViewExternalIT}.
 * Missing configuration is a failure, never a JUnit skip.
 */
class AloudataAnalysisViewExternalIT {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    void verifiesDirectoryDetailResultAndRemoteFilter() throws Exception {
        Config config = Config.fromEnvironment();
        Map<String, Object> tree = get(config.productUrl + "/anymetrics/api/v1/analysisview/treeList", config);
        assertEquals("200", text(tree, "code"), "ALO-X01 directory must succeed");

        Map<String, Object> detail = get(config.productUrl + "/anymetrics/api/v1/analysisview/queryByName?viewName="
                + encode(config.viewName), config);
        assertEquals("200", text(detail, "code"), "ALO-X01 detail must succeed");

        Map<String, Object> baseline = get(config.semanticUrl + "/semantic/api/v1.1/analysisView/query?viewName="
                + encode(config.viewName) + "&pageIndex=0&pageSize=5&queryResultType=DATA", config);
        assertNotEquals("SM_02_0038", text(baseline, "code"),
                "configured view is not authorized for result query");
        assertTrue(Boolean.TRUE.equals(baseline.get("success")),
                "ALO-X02 baseline result query must succeed: " + safeStatus(baseline));

        Map<String, Object> data = asMap(detail.get("data"));
        if (data.isEmpty()) data = detail;
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("metrics", names(data.get("metrics")));
        query.put("dimensions", names(data.get("dimensions")));
        query.put("filters", List.of(Map.of("field", config.filterColumn,
                "operator", "eq", "value", config.filterValue)));
        query.put("limit", 5);
        query.put("offset", 0);
        query.put("queryResultType", "DATA");
        Map<String, Object> filtered = post(config.semanticUrl + "/semantic/api/v1.1/metrics/query", config, query);
        assertTrue(Boolean.TRUE.equals(filtered.get("success")),
                "ALO-X02 filtered metrics query must succeed: " + safeStatus(filtered));
        assertNotEquals(mapper.writeValueAsString(resultPayload(baseline)),
                mapper.writeValueAsString(resultPayload(filtered)),
                "remote response must change after the configured filter");
    }

    private Map<String, Object> get(String url, Config config) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("tenant-id", config.tenantId)
                .header("auth-type", config.authType)
                .header("auth-value", config.authValue)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Aloudata HTTP status for " + path(url));
        return mapper.readValue(response.body(), new TypeReference<>() {});
    }

    private Map<String, Object> post(String url, Config config, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("tenant-id", config.tenantId)
                .header("auth-type", config.authType)
                .header("auth-value", config.authValue)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Aloudata HTTP status for " + path(url));
        return mapper.readValue(response.body(), new TypeReference<>() {});
    }

    private List<String> names(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::name).filter(s -> !s.isBlank()).toList();
    }

    private String name(Object value) {
        if (!(value instanceof Map<?, ?> map)) return String.valueOf(value);
        for (String key : List.of("name", "metricName", "dimName", "code")) {
            if (map.get(key) != null && !String.valueOf(map.get(key)).isBlank()) return String.valueOf(map.get(key));
        }
        return "";
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? mapper.convertValue(map, new TypeReference<>() {}) : Map.of();
    }

    private Object resultPayload(Map<String, Object> body) {
        if (body.containsKey("analysisView")) return body.get("analysisView");
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map && map.containsKey("rows")) return map.get("rows");
        return data;
    }

    private String text(Map<String, Object> body, String key) {
        return body.get(key) == null ? null : String.valueOf(body.get(key));
    }

    private String safeStatus(Map<String, Object> body) {
        return "code=" + text(body, "code") + ", success=" + body.get("success");
    }

    private String path(String url) {
        try { return URI.create(url).getPath(); } catch (Exception ignored) { return "<invalid-url>"; }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Config(String productUrl, String semanticUrl, String tenantId, String authType,
                          String authValue, long datasourceId, String viewName,
                          String filterColumn, String filterValue) {
        static Config fromEnvironment() {
            require("ALOU_DATA_EXTERNAL_TEST", true);
            return new Config(
                    require("ALOU_DATA_PRODUCT_BASE_URL", false),
                    require("ALOU_DATA_SEMANTIC_BASE_URL", false),
                    require("ALOU_DATA_TENANT_ID", false),
                    require("ALOU_DATA_AUTH_TYPE", false),
                    require("ALOU_DATA_AUTH_VALUE", false),
                    Long.parseLong(require("ALOU_DATA_TEST_DATASOURCE_ID", false)),
                    require("ALOU_DATA_TEST_VIEW_NAME", false),
                    require("ALOU_DATA_TEST_FILTER_COLUMN", false),
                    require("ALOU_DATA_TEST_FILTER_VALUE", false));
        }

        private static String require(String name, boolean truthy) {
            String value = System.getenv(name);
            if (value == null || value.isBlank() || (truthy && !Boolean.parseBoolean(value))) {
                throw new AssertionError("Missing or invalid required external-test environment variable: " + name);
            }
            return value;
        }
    }
}
