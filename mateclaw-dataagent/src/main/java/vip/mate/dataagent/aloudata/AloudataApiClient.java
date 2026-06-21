package vip.mate.dataagent.aloudata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import vip.mate.dataagent.aloudata.AloudataApiProperties.ApiEndpoint;
import vip.mate.dataagent.dto.AloudataConfigDTO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aloudata 通用 API 客户端
 * <p>
 * 配置驱动的 HTTP 客户端，根据端点名称从 {@link AloudataEndpointService} 查找路径配置，
 * 拼接完整 URL 后发起请求。支持数据源级别的端点路径覆盖。
 * <p>
 * 认证方式：直接在请求 Header 中注入 tenant-id / auth-type / auth-value，
 * 从 {@link AloudataConfigDTO} 中获取，无需预先获取 Token。
 * <p>
 * API 版本升级时，只需修改数据库中 {@code aloudata.api.endpoints} 配置即可，
 * 无需改动代码。
 * <p>
 * 支持两种调用方式：
 * <ul>
 *   <li>{@link #call} — 手动构建请求体，兼容已有调用方</li>
 *   <li>{@link #callWithParams} — 传入参数 Map，基于 ApiParam 定义自动校验并分发参数到
 *       HEADER/PATH/QUERY/BODY</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AloudataApiClient {

    private final AloudataEndpointService endpointService;
    private final RestTemplate restTemplate = new RestTemplate();

    /** anymetrics 默认端口 */
    private static final int DEFAULT_ANYMETRICS_PORT = 8083;

    /** semantic 默认端口 */
    private static final int DEFAULT_SEMANTIC_PORT = 8085;

    /**
     * 根据端点名称调用 API（无请求体）
     *
     * @param endpointName  端点名称（对应配置中的 key）
     * @param config        连接配置（自动注入认证 Header）
     * @param pathVariables 路径变量（可为 null）
     * @return 原始响应体
     */
    public ResponseEntity<Map> call(String endpointName, AloudataConfigDTO config,
                                    Map<String, String> pathVariables) {
        return call(endpointName, config, pathVariables, null);
    }

    /**
     * 根据端点名称调用 API（带请求体）
     *
     * @param endpointName  端点名称（对应配置中的 key）
     * @param config        连接配置（自动注入认证 Header）
     * @param pathVariables 路径变量（可为 null）
     * @param requestBody   请求体（可为 null）
     * @return 原始响应体
     */
    public ResponseEntity<Map> call(String endpointName, AloudataConfigDTO config,
                                    Map<String, String> pathVariables,
                                    Object requestBody) {
        String path = resolvePath(endpointName, config);
        String url = buildUrl(endpointName, path, config, pathVariables);

        HttpHeaders headers = buildAuthHeaders(config);

        HttpMethod method = resolveMethod(endpointName);
        HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);

        log.debug("调用 Aloudata API: {} {}", method, url);
        return restTemplate.exchange(url, method, entity, Map.class);
    }

    /**
     * 基于参数规范的 API 调用
     * <p>
     * 传入参数 Map 后，根据端点的 ApiParam 定义自动完成：
     * <ol>
     *   <li>必填参数校验 — 缺少必填参数时抛出 IllegalArgumentException</li>
     *   <li>枚举值校验 — 参数值不在允许范围内时抛出 IllegalArgumentException</li>
     *   <li>参数分发 — 按 paramLocation 将参数分配到 HTTP Header / URL 路径 / URL 查询参数 / 请求体</li>
     * </ol>
     * <p>
     * 认证 Header（tenant-id / auth-type / auth-value）从 config 自动注入，
     * 如果参数 Map 中也提供了这些值，以参数 Map 为准（支持覆盖）。
     *
     * @param endpointName 端点名称
     * @param config       连接配置（自动注入认证 Header）
     * @param params       参数 Map，key 为参数名，value 为参数值
     * @return 原始响应体
     */
    public ResponseEntity<Map> callWithParams(String endpointName, AloudataConfigDTO config,
                                              Map<String, Object> params) {
        ApiEndpoint endpoint = endpointService.getEndpoint(endpointName);
        if (endpoint == null) {
            throw new IllegalArgumentException("未定义的 API 端点: " + endpointName);
        }

        // 合并默认值：对有 defaultValue 但未传入的参数自动填充
        Map<String, Object> effectiveParams = applyDefaults(endpoint, params);

        // 参数校验
        validateParams(endpoint, effectiveParams);

        // 按参数位置分发
        HttpHeaders headers = buildAuthHeaders(config);

        Map<String, String> pathVariables = new LinkedHashMap<>();
        List<String[]> queryParams = new ArrayList<>();
        Map<String, Object> bodyParams = new LinkedHashMap<>();

        for (ApiParam paramDef : endpoint.getRequestParams()) {
            Object value = effectiveParams.get(paramDef.getName());
            if (value == null) {
                continue;
            }
            String location = paramDef.getParamLocation();
            if (location == null) {
                continue;
            }
            switch (location.toUpperCase()) {
                case "HEADER" -> headers.set(paramDef.getName(), String.valueOf(value));
                case "PATH" -> pathVariables.put(paramDef.getName(), String.valueOf(value));
                case "QUERY" -> {
                    /* Array 类型参数展开为重复参数名：key=v1&key=v2 */
                    if (value instanceof Collection<?> collection) {
                        for (Object item : collection) {
                            if (item != null) {
                                queryParams.add(new String[]{paramDef.getName(), String.valueOf(item)});
                            }
                        }
                    } else if (value.getClass().isArray()) {
                        for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                            Object item = java.lang.reflect.Array.get(value, i);
                            if (item != null) {
                                queryParams.add(new String[]{paramDef.getName(), String.valueOf(item)});
                            }
                        }
                    } else {
                        queryParams.add(new String[]{paramDef.getName(), String.valueOf(value)});
                    }
                }
                case "BODY" -> bodyParams.put(paramDef.getName(), value);
                default -> log.warn("未知的参数位置类型 [{}]: {}", paramDef.getName(), location);
            }
        }

        // 构建请求
        String path = resolvePath(endpointName, config);
        String url = buildUrl(endpointName, path, config, pathVariables);

        // 追加查询参数
        if (!queryParams.isEmpty()) {
            String queryString = queryParams.stream()
                    .map(pair -> pair[0] + "=" + pair[1])
                    .collect(Collectors.joining("&"));
            url += (url.contains("?") ? "&" : "?") + queryString;
        }

        Object requestBody = bodyParams.isEmpty() ? null : bodyParams;
        HttpMethod method = resolveMethod(endpointName);
        HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);

        log.debug("调用 Aloudata API (参数规范): {} {}", method, url);
        return restTemplate.exchange(url, method, entity, Map.class);
    }

    /**
     * 构建认证 Header
     * <p>
     * 从 AloudataConfigDTO 中提取 tenant-id / auth-type / auth-value，
     * 直接注入到请求 Header 中，无需预先获取 Token。
     *
     * @param config 连接配置
     * @return 包含认证信息的 HttpHeaders
     */
    private HttpHeaders buildAuthHeaders(AloudataConfigDTO config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("tenant-id", config.getTenantId());
        headers.set("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
        headers.set("auth-value", config.getAuthValue());
        return headers;
    }

    /**
     * 为未传入的参数填充默认值
     */
    private Map<String, Object> applyDefaults(ApiEndpoint endpoint, Map<String, Object> params) {
        Map<String, Object> effective = new LinkedHashMap<>(params != null ? params : Collections.emptyMap());
        if (endpoint.getRequestParams() == null) {
            return effective;
        }
        for (ApiParam paramDef : endpoint.getRequestParams()) {
            if (!effective.containsKey(paramDef.getName())
                    && paramDef.getDefaultValue() != null && !paramDef.getDefaultValue().isBlank()) {
                effective.put(paramDef.getName(), parseDefaultValue(paramDef));
            }
        }
        return effective;
    }

    /**
     * 将默认值字符串按参数类型转换为对应 Java 类型
     */
    private Object parseDefaultValue(ApiParam paramDef) {
        String defaultVal = paramDef.getDefaultValue();
        String type = paramDef.getType() != null ? paramDef.getType() : "String";
        try {
            if (type.contains("Integer") || type.contains("int")) {
                return Integer.parseInt(defaultVal);
            } else if (type.contains("Long") || type.contains("long")) {
                return Long.parseLong(defaultVal);
            } else if (type.contains("Boolean") || type.contains("boolean")) {
                return Boolean.parseBoolean(defaultVal);
            } else if (type.contains("Double") || type.contains("double")) {
                return Double.parseDouble(defaultVal);
            }
        } catch (NumberFormatException e) {
            log.warn("参数 [{}] 默认值 '{}' 无法按类型 {} 解析，保留字符串", paramDef.getName(), defaultVal, type);
        }
        return defaultVal;
    }

    /**
     * 参数校验：必填检查 + 枚举值检查
     *
     * @param endpoint 端点定义
     * @param params   传入的参数（已填充默认值）
     * @throws IllegalArgumentException 校验不通过时抛出
     */
    private void validateParams(ApiEndpoint endpoint, Map<String, Object> params) {
        if (endpoint.getRequestParams() == null) {
            return;
        }
        List<String> errors = new ArrayList<>();

        for (ApiParam paramDef : endpoint.getRequestParams()) {
            String paramName = paramDef.getName();
            Object value = params.get(paramName);

            // 必填校验
            if (Boolean.TRUE.equals(paramDef.getRequired()) && value == null) {
                errors.add(String.format("参数 '%s' 为必填项（说明: %s）", paramName,
                        paramDef.getDescription() != null ? paramDef.getDescription() : ""));
                continue;
            }

            // 枚举值校验
            if (value != null && paramDef.getEnumValues() != null && !paramDef.getEnumValues().isBlank()) {
                Set<String> allowedValues = Arrays.stream(paramDef.getEnumValues().split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());
                String strValue = String.valueOf(value);
                if (!allowedValues.contains(strValue)) {
                    errors.add(String.format("参数 '%s' 的值 '%s' 不在允许范围 %s 内",
                            paramName, strValue, allowedValues));
                }
            }
        }

        if (!errors.isEmpty()) {
            String message = "API 参数校验失败 [" + endpoint.getPath() + "]: " + String.join("; ", errors);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 解析端点路径：优先使用数据源级别覆盖，其次使用全局配置
     */
    private String resolvePath(String endpointName, AloudataConfigDTO config) {
        // 1. 优先使用数据源级别的覆盖
        if (config.getApiOverrides() != null && config.getApiOverrides().containsKey(endpointName)) {
            String overridePath = config.getApiOverrides().get(endpointName);
            if (overridePath != null && !overridePath.isBlank()) {
                log.debug("使用数据源级覆盖路径 [{}]: {}", endpointName, overridePath);
                return overridePath;
            }
        }

        // 2. 使用全局配置
        ApiEndpoint endpoint = endpointService.getEndpoint(endpointName);
        if (endpoint != null && endpoint.getPath() != null) {
            return endpoint.getPath();
        }

        throw new IllegalArgumentException("未定义的 API 端点: " + endpointName
                + "，请在系统配置 aloudata.api.endpoints 中添加该端点");
    }

    /**
     * 构建 URL：base URL + 路径 + 路径变量替换
     */
    private String buildUrl(String endpointName, String path, AloudataConfigDTO config,
                            Map<String, String> pathVariables) {
        String baseUrl = resolveBaseUrl(endpointName, config);
        String resolvedPath = replacePathVariables(path, pathVariables);
        return baseUrl + resolvedPath;
    }

    /**
     * 根据 service 名解析 base URL
     */
    private String resolveBaseUrl(String endpointName, AloudataConfigDTO config) {
        ApiEndpoint endpoint = endpointService.getEndpoint(endpointName);
        String service = (endpoint != null) ? endpoint.getService() : "anymetrics";

        return switch (service) {
            case "semantic" -> String.format("%s:%d",
                    config.getSemanticHost(),
                    config.getSemanticPort() != null ? config.getSemanticPort() : DEFAULT_SEMANTIC_PORT);
            default -> String.format("%s:%d",
                    config.getAnymetricsHost(),
                    config.getAnymetricsPort() != null ? config.getAnymetricsPort() : DEFAULT_ANYMETRICS_PORT);
        };
    }

    /**
     * 解析 HTTP 方法
     */
    private HttpMethod resolveMethod(String endpointName) {
        ApiEndpoint endpoint = endpointService.getEndpoint(endpointName);
        if (endpoint != null && endpoint.getMethod() != null) {
            try {
                return HttpMethod.valueOf(endpoint.getMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("无效的 HTTP 方法配置 [{}]: {}，默认使用 GET", endpointName, endpoint.getMethod());
            }
        }
        return HttpMethod.GET;
    }

    /**
     * 替换路径中的 {var} 占位符
     */
    private String replacePathVariables(String path, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return path;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return path;
    }
}
