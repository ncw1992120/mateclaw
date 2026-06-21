package vip.mate.dataagent.aloudata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.AloudataApiProperties.ApiEndpoint;

import vip.mate.dataagent.dto.AloudataConfigDTO;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aloudata API 端点业务服务
 * <p>
 * 承载端点配置的全部业务逻辑：
 * <ul>
 *   <li>端点配置获取（数据库优先 + 默认兜底 + 缓存决策）</li>
 *   <li>默认端点配置构建（代码级兜底）</li>
 *   <li>公共请求头参数生成</li>
 *   <li>参数合并</li>
 * </ul>
 * <p>
 * 配置类 {@link AloudataApiProperties} 仅负责从数据库读取原始配置值，
 * 不夹带任何业务处理逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AloudataEndpointService {

    private final AloudataApiProperties apiProperties;

    /** 缓存的端点配置 */
    private volatile Map<String, ApiEndpoint> cachedEndpoints;

    /** 缓存时间戳 */
    private volatile long cacheTimestamp = 0;

    /** 缓存有效期（毫秒），5 分钟 */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 获取所有端点配置（缓存 → 数据库 → 默认兜底）
     *
     * @return 端点配置 Map，不会返回 null
     */
    public Map<String, ApiEndpoint> getEndpoints() {
        // 1. 缓存有效则直接返回
        if (cachedEndpoints != null && !cachedEndpoints.isEmpty()
                && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedEndpoints;
        }

        // 2. 加锁刷新
        synchronized (this) {
            if (cachedEndpoints != null && !cachedEndpoints.isEmpty()
                    && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
                return cachedEndpoints;
            }

            // 3. 从数据库读取
            Map<String, ApiEndpoint> fromDb = apiProperties.readEndpoints();
            if (fromDb != null && !fromDb.isEmpty()) {
                cachedEndpoints = fromDb;
            } else {
                // 4. 数据库无配置，使用默认兜底
                cachedEndpoints = getDefaultEndpoints();
            }
            cacheTimestamp = System.currentTimeMillis();
            return cachedEndpoints;
        }
    }

    /**
     * 根据端点名称获取配置（缓存 → 数据库 → 默认兜底）
     *
     * @param name 端点名称
     * @return 端点配置，不存在则返回 null
     */
    public ApiEndpoint getEndpoint(String name) {
        Map<String, ApiEndpoint> endpoints = getEndpoints();
        return endpoints.get(name);
    }

    /**
     * 强制刷新缓存
     */
    public void refresh() {
        cacheTimestamp = 0;
        cachedEndpoints = null;
    }

    /**
     * 获取 API 版本号
     */
    public String getVersion() {
        return apiProperties.readVersion();
    }

    /**
     * 默认端点配置（代码级兜底，数据库无配置时使用）
     * <p>
     * 请求参数和响应参数规范基于 Aloudata CAN 官方 API 文档定义。
     * 认证方式：直接在请求 Header 中传递 tenant-id / auth-type / auth-value，
     * 无需预先获取 Token。公共请求头参数通过 commonHeaderParams() 统一生成。
     */
    private Map<String, ApiEndpoint> getDefaultEndpoints() {
        return new LinkedHashMap<>();
    }

    /**
     * 生成公共请求头参数（认证接口之外的所有接口均需传递）
     */
    private List<ApiParam> commonHeaderParams() {
        return List.of(
                new ApiParam("tenant-id", "String", true, null, "租户ID", "HEADER"),
                new ApiParam("auth-type", "String", true, null, "认证方式", "HEADER", "UID,TOKEN,ACCOUNT,APIKEY"),
                new ApiParam("auth-value", "String", true, null, "与auth-type对应的认证值", "HEADER"),
                new ApiParam("query-user-account", "String", false, null, "鉴权用户名，为空则使用auth-value对应用户", "HEADER")
        );
    }

    /**
     * 合并公共请求头参数和接口特有参数
     */
    private List<ApiParam> mergeParams(List<ApiParam> headerParams, List<ApiParam> specificParams) {
        List<ApiParam> merged = new ArrayList<>(headerParams);
        merged.addAll(specificParams);
        return merged;
    }

    /**
     * 从配置中构建指定端点的 HEADER 参数 Map
     * <p>
     * 根据端点定义的 HEADER 类型 requestParams，从 AloudataConfigDTO 中提取对应的值。
     * 支持动态扩展：当端点配置新增 HEADER 参数时，只需在 resolveConfigValue 中添加对应的取值逻辑。
     * 端点未定义时降级为基础认证参数。
     *
     * @param endpointName 端点名称
     * @param config       连接配置
     * @return HEADER 参数 Map
     */
    public Map<String, Object> buildHeaderParamsFromConfig(String endpointName, AloudataConfigDTO config) {
        Map<String, Object> params = new LinkedHashMap<>();
        ApiEndpoint endpoint = getEndpoint(endpointName);
        if (endpoint == null || endpoint.getRequestParams() == null) {
            // 降级：端点未定义时使用基础认证参数
            params.put("tenant-id", config.getTenantId());
            params.put("auth-type", config.getAuthType() != null ? config.getAuthType() : "UID");
            params.put("auth-value", config.getAuthValue());
            return params;
        }
        for (ApiParam paramDef : endpoint.getRequestParams()) {
            if (!"HEADER".equalsIgnoreCase(paramDef.getParamLocation())) {
                continue;
            }
            Object value = resolveConfigValue(paramDef.getName(), config);
            if (value != null) {
                params.put(paramDef.getName(), value);
            }
        }
        return params;
    }

    /**
     * 根据端点参数定义合并 HEADER 参数和业务输入参数
     * <p>
     * 业务输入只会合并端点 requestParams 中声明的非 HEADER 参数，避免调用方硬编码 API 参数清单。
     *
     * @param endpointName 端点名称
     * @param config       连接配置
     * @param inputParams  业务输入参数
     * @return API 调用参数 Map
     */
    public Map<String, Object> buildParamsFromConfigAndInput(String endpointName, AloudataConfigDTO config,
                                                              Map<String, Object> inputParams) {
        Map<String, Object> params = buildHeaderParamsFromConfig(endpointName, config);
        ApiEndpoint endpoint = getEndpoint(endpointName);
        if (endpoint == null || endpoint.getRequestParams() == null || inputParams == null || inputParams.isEmpty()) {
            return params;
        }
        for (ApiParam paramDef : endpoint.getRequestParams()) {
            if ("HEADER".equalsIgnoreCase(paramDef.getParamLocation())) {
                continue;
            }
            Object value = inputParams.get(paramDef.getName());
            if (value != null) {
                params.put(paramDef.getName(), value);
            }
        }
        return params;
    }

    /**
     * 根据端点参数定义从输入对象中动态读取业务参数
     *
     * @param endpointName 端点名称
     * @param config       连接配置
     * @param input        输入对象
     * @return API 调用参数 Map
     */
    public Map<String, Object> buildParamsFromConfigAndInput(String endpointName, AloudataConfigDTO config,
                                                              Object input) {
        Map<String, Object> params = buildHeaderParamsFromConfig(endpointName, config);
        ApiEndpoint endpoint = getEndpoint(endpointName);
        if (endpoint == null || endpoint.getRequestParams() == null || input == null) {
            return params;
        }
        for (ApiParam paramDef : endpoint.getRequestParams()) {
            if ("HEADER".equalsIgnoreCase(paramDef.getParamLocation())) {
                continue;
            }
            Object value = readProperty(input, paramDef.getName());
            if (value != null) {
                params.put(paramDef.getName(), value);
            }
        }
        return params;
    }

    /**
     * 按端点 responseParams 定义，将响应 Map 中的字段动态映射到 DTO 对象
     * <p>
     * 仅处理 {@code data[]} 层级的字段（形如 {@code data[].fieldName}），
     * 自动跳过响应级通用字段（success/code/errorMsg/traceId）。
     * 字段名到 DTO setter 的映射规则：
     * <ol>
     *   <li>先查 fieldAliasMap（处理不一致映射，如 id → metricId）</li>
     *   <li>再尝试同名 setter（如 metricName → setMetricName）</li>
     * </ol>
     *
     * @param endpointName 端点名称
     * @param source       响应中的单条数据 Map
     * @param target       目标 DTO 对象
     * @param fieldAliasMap 字段别名映射（key=API 字段名, value=DTO 属性名），可为 null
     */
    public void mapResponseToDto(String endpointName, Map<String, Object> source,
                                  Object target, Map<String, String> fieldAliasMap) {
        ApiEndpoint endpoint = getEndpoint(endpointName);
        if (endpoint == null || endpoint.getResponseParams() == null || source == null || target == null) {
            return;
        }
        for (ApiParam paramDef : endpoint.getResponseParams()) {
            String name = paramDef.getName();
            // 只处理 data[] 层级的字段
            if (!name.startsWith("data[].")) {
                continue;
            }
            String apiFieldName = name.substring("data[].".length());
            Object value = source.get(apiFieldName);
            if (value == null) {
                continue;
            }
            // 优先使用别名映射，其次使用 API 字段名本身
            String dtoPropertyName = (fieldAliasMap != null && fieldAliasMap.containsKey(apiFieldName))
                    ? fieldAliasMap.get(apiFieldName) : apiFieldName;
            writeProperty(target, dtoPropertyName, value);
        }
    }

    /**
     * 按端点 responseParams 定义，将响应 Map 中的字段动态映射到 DTO 对象
     * <p>
     * 无别名映射，直接按 API 字段名匹配 DTO 属性
     */
    public void mapResponseToDto(String endpointName, Map<String, Object> source, Object target) {
        mapResponseToDto(endpointName, source, target, null);
    }

    /**
     * 根据 HEADER 参数名从配置中解析对应的值
     */
    private Object resolveConfigValue(String paramName, AloudataConfigDTO config) {
        return switch (paramName) {
            case "tenant-id" -> config.getTenantId();
            case "auth-type" -> config.getAuthType() != null ? config.getAuthType() : "UID";
            case "auth-value" -> config.getAuthValue();
            default -> null;
        };
    }

    /**
     * 按参数名读取对象属性
     */
    private Object readProperty(Object input, String paramName) {
        String propertyName = paramName.replace("-", "_");
        Object getterValue = invokeNoArgMethod(input, "get" + toUpperCamel(propertyName));
        if (getterValue != null) {
            return getterValue;
        }
        return invokeNoArgMethod(input, propertyName);
    }

    /**
     * 按属性名写入对象属性
     */
    private void writeProperty(Object target, String propertyName, Object value) {
        String setterName = "set" + toUpperCamel(propertyName);
        try {
            Method[] methods = target.getClass().getDeclaredMethods();
            for (Method m : methods) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    Object converted = convertValue(value, paramType);
                    if (converted != null) {
                        m.setAccessible(true);
                        m.invoke(target, converted);
                    }
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            log.debug("写入属性 [{}.{}] 失败: {}", target.getClass().getSimpleName(), propertyName, e.getMessage());
        }
    }

    /**
     * 将值转换为目标类型
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    /**
     * 调用无参方法
     */
    private Object invokeNoArgMethod(Object input, String methodName) {
        try {
            Method method = input.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(input);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * 转换为 UpperCamel 命名
     */
    private String toUpperCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = true;
        for (char ch : value.toCharArray()) {
            if (ch == '_' || ch == '-') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                result.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }
}