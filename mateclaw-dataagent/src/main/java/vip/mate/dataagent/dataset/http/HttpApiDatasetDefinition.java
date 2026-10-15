package vip.mate.dataagent.dataset.http;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** 已登记的 HTTP API 数据集定义；脚本只能影响声明的参数。 */
public record HttpApiDatasetDefinition(
        URI endpoint,
        String method,
        List<String> allowedQueryParams,
        List<String> allowedBodyParams,
        String resultPath,
        String paginationMode,
        String pageParam,
        String sizeParam,
        boolean idempotent,
        Map<String, Object> fixedBody) {
    public HttpApiDatasetDefinition {
        if (endpoint == null || method == null || method.isBlank()) throw new IllegalArgumentException("endpoint and method are required");
        allowedQueryParams = allowedQueryParams == null ? List.of() : List.copyOf(allowedQueryParams);
        allowedBodyParams = allowedBodyParams == null ? List.of() : List.copyOf(allowedBodyParams);
        fixedBody = fixedBody == null ? Map.of() : Map.copyOf(fixedBody);
        paginationMode = paginationMode == null ? "none" : paginationMode;
    }
}
