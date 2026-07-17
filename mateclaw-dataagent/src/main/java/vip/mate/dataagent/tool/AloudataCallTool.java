package vip.mate.dataagent.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.agent.context.ChatOriginHolder;
import vip.mate.dataagent.aloudata.AloudataApiClient;
import vip.mate.dataagent.aloudata.AloudataApiProperties.ApiEndpoint;
import vip.mate.dataagent.aloudata.AloudataConfigHelper;
import vip.mate.dataagent.aloudata.AloudataEndpointService;
import vip.mate.dataagent.aloudata.ApiParam;
import vip.mate.dataagent.auth.context.UserContextHolder;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.AloudataMetricDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.*;
import vip.mate.dataagent.support.DataAgentChatScopeContext;
import vip.mate.dataagent.support.DataAgentChatScopeContext.ScopeResolveResult;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Aloudata API 动态工具注册器
 * <p>
 * 根据 {@code aloudata.api.endpoints} 配置，在启动时为每个 API 端点动态注册一个独立的 Agent Tool。
 * API 端点的新增、删除、路径变更只需修改数据库配置，无需改动代码。
 * <p>
 * 注册规则：
 * <ul>
 *   <li>Tool 名称格式：{@code aloudata_{endpointName}}（如 aloudata_metrics_list）</li>
 *   <li>每个 Tool 的 inputSchema 根据端点的 requestParams（排除 HEADER 参数）动态生成</li>
 *   <li>所有 Tool 自动注入 datasourceId 参数用于定位本地系统中配置的指标平台数据源（非 Aloudata API 参数）</li>
 *   <li>认证方式：tenant-id / auth-type / auth-value 从数据源配置中获取，由 ApiClient 自动注入 Header</li>
 * </ul>
 * <p>
 * 此外额外注册 {@code aloudata_search_semantic} Tool，用于搜索本地语义模型（不走 API）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AloudataCallTool {

    private final AloudataEndpointService endpointService;
    private final AloudataApiClient apiClient;
    private final AloudataConfigHelper configHelper;
    private final SemanticModelService semanticModelService;
    private final SchemaEmbeddingService schemaEmbeddingService;
    private final AloudataSemanticEsService aloudataSemanticEsService;
    private final AloudataSemanticSyncService aloudataSemanticSyncService;
    private final BusinessTermEsService businessTermEsService;
    private final DatasourceMapper datasourceMapper;
    private final DatasourceAccountService datasourceAccountService;
    private final AloudataMetricDimensionMapper metricDimensionMapper;
    private final AloudataMetricMapper metricMapper;
    private final MateClawRuntime mateClawRuntime;
    private final DataAgentChatScopeContext scopeContext;

    /** 指标查询端点名，需要 ECharts 图表生成等增值逻辑 */
    private static final String METRICS_QUERY_ENDPOINT = "metrics_query";

    /** 术语检索工具名 */
    private static final String SEARCH_BUSINESS_TERM_TOOL_NAME = "search_business_term";

    /** Tool 名称前缀 */
    private static final String TOOL_PREFIX = "aloudata_";

    /** 语义搜索 Tool 名称 */
    private static final String SEARCH_SEMANTIC_TOOL_NAME = "aloudata_search_semantic";

    /** Markdown 表格展示阈值 */
    private static final int MARKDOWN_TABLE_THRESHOLD = 20;

    /** Markdown 表格最大列数 */
    private static final int MAX_COLUMNS_FOR_TABLE = 10;

    /** 日期粒度等级（从小到大），用于同环比偏移粒度校验 */
    private static final Map<String, Integer> GRAIN_LEVEL = Map.of(
            "day", 1, "week", 2, "month", 3, "quarter", 4, "year", 5
    );

    /** 偏移粒度到日期粒度的映射 */
    private static final Map<String, String> OFFSET_GRAIN_MAP = Map.ofEntries(
            Map.entry("dod", "day"), Map.entry("wow", "week"), Map.entry("mom", "month"),
            Map.entry("qoq", "quarter"), Map.entry("yoy", "year"),
            Map.entry("woeow", "week"), Map.entry("moeom", "month"), Map.entry("qoeoq", "quarter"),
            Map.entry("yoeoy", "year"), Map.entry("wosow", "week"), Map.entry("mosom", "month"),
            Map.entry("qosoq", "quarter"), Map.entry("yosoy", "year")
    );

    /**
     * 启动时动态注册所有 Aloudata API 端点为独立 Tool
     */
    @PostConstruct
    public void registerDynamicTools() {
        Map<String, ApiEndpoint> endpoints = endpointService.getEndpoints();
        if (MapUtils.isEmpty(endpoints)) {
            log.warn("Aloudata API 端点配置为空，跳过动态 Tool 注册");
            return;
        }

        int registeredCount = 0;
        for (Map.Entry<String, ApiEndpoint> entry : endpoints.entrySet()) {
            String endpointName = entry.getKey();
            ApiEndpoint endpoint = entry.getValue();

            String toolName = TOOL_PREFIX + endpointName;
            String description = buildDescription(endpointName, endpoint);
            String inputSchema = buildInputSchema(endpoint);

            SkillScopedToolCallback toolCallback = new SkillScopedToolCallback(
                    toolName,
                    description,
                    inputSchema,
                    toolInput -> handleDynamicToolCall(endpointName, toolInput)
            );
            mateClawRuntime.registerTool(toolCallback);
            registeredCount++;
            log.info("动态注册 Aloudata Tool: {} -> {}", toolName, endpoint.getPath());
        }

        // 注册语义搜索 Tool（查本地语义模型，不走 API）
        registerSearchSemanticTool();

        // 注册业务术语检索 Tool（查业务术语和同义词，不走 API）
        registerSearchBusinessTermTool();

        log.info("Aloudata 动态 Tool 注册完成，共注册 {} 个 API Tool + 1 个语义搜索 Tool + 1 个术语检索 Tool", registeredCount);
    }

    /**
     * 注册语义搜索 Tool
     */
    private void registerSearchSemanticTool() {
        String description = "搜索 Aloudata 指标/维度的本地语义模型，"
                + "返回匹配的业务名称、业务描述、同义词等信息，帮助理解用户意图。"
                + "使用 Elasticsearch 进行关键词检索和向量语义检索的混合模式（RRF 融合），"
                + "ES 不可用时自动降级为 MySQL 模糊匹配。"
                + "需要 datasourceId 和 keyword 参数。";

        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "datasourceId": {
                      "type": "integer",
                      "description": "本地系统中配置的指标平台数据源 ID（不会传递给远程 API）"
                    },
                    "keyword": {
                      "type": "string",
                      "description": "搜索关键词，用于搜索指标/维度的业务名称、同义词等"
                    },
                    "topK": {
                      "type": "integer",
                      "description": "返回结果数量上限，默认10"
                    },
                    "similarityThreshold": {
                      "type": "number",
                      "description": "向量语义检索相似度阈值（0-1），默认0.3，越低返回越多结果"
                    }
                  },
                  "required": ["datasourceId", "keyword"]
                }
                """;

        SkillScopedToolCallback toolCallback = new SkillScopedToolCallback(
                SEARCH_SEMANTIC_TOOL_NAME,
                description,
                inputSchema,
                this::handleSearchSemantic
        );
        mateClawRuntime.registerTool(toolCallback);
    }

    /**
     * 注册业务术语检索 Tool
     */
    private void registerSearchBusinessTermTool() {
        String description = "搜索业务术语和同义词词典，"
                + "返回匹配的术语名称、定义、同义词、分类等信息，帮助理解用户查询中的业务术语含义。"
                + "当用户提问中包含业务术语、缩写或别名时，应先调用此工具查询其标准术语名和定义，"
                + "再结合语义搜索工具查找对应的指标或维度。"
                + "使用 Elasticsearch 进行关键词检索和向量语义检索的混合模式（RRF 融合），"
                + "ES 不可用时自动降级为 MySQL 模糊匹配。"
                + "跨所有业务域检索术语，需要 keyword 参数。";

        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "keyword": {
                      "type": "string",
                      "description": "搜索关键词，用于搜索术语名称、同义词、定义等"
                    },
                    "topK": {
                      "type": "integer",
                      "description": "返回结果数量上限，默认10"
                    },
                    "similarityThreshold": {
                      "type": "number",
                      "description": "向量语义检索相似度阈值（0-1），默认0.3，越低返回越多结果"
                    }
                  },
                  "required": ["keyword"]
                }
                """;

        SkillScopedToolCallback termToolCallback = new SkillScopedToolCallback(
                SEARCH_BUSINESS_TERM_TOOL_NAME,
                description,
                inputSchema,
                this::handleSearchBusinessTerm
        );
        mateClawRuntime.registerTool(termToolCallback);
    }

    /**
     * 构建 Tool 描述
     */
    private String buildDescription(String endpointName, ApiEndpoint endpoint) {
        StringBuilder sb = new StringBuilder();
        sb.append(endpoint.getDescription() != null ? endpoint.getDescription() : endpointName);

        // 附加响应参数摘要，帮助 Agent 理解返回结构
        if (endpoint.getResponseParams() != null && !endpoint.getResponseParams().isEmpty()) {
            List<String> keyFields = endpoint.getResponseParams().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getRequired()))
                    .map(p -> p.getName() + "(" + p.getDescription() + ")")
                    .limit(5)
                    .toList();
            if (!keyFields.isEmpty()) {
                sb.append("。主要返回字段：").append(String.join("、", keyFields));
            }
        }

        sb.append("。需要 datasourceId 参数指定本地系统中配置的指标平台数据源。");
        return sb.toString();
    }

    /**
     * 根据端点的 requestParams 动态生成 inputSchema JSON
     * <p>
     * 排除 HEADER 类型参数（认证信息由 datasourceId 自动解析填充），
     * 自动注入 datasourceId 参数（本地系统数据源标识，不传递给远程 API）。
     */
    private String buildInputSchema(ApiEndpoint endpoint) {
        JSONObject schema = new JSONObject(new LinkedHashMap<>());
        schema.set("type", "object");

        JSONObject properties = new JSONObject(new LinkedHashMap<>());

        // 自动注入 datasourceId
        JSONObject datasourceIdProp = new JSONObject(new LinkedHashMap<>());
        datasourceIdProp.set("type", "integer");
        datasourceIdProp.set("description", "本地系统中配置的指标平台数据源 ID（用于定位连接配置和认证信息，不会传递给远程 API）");
        properties.set("datasourceId", datasourceIdProp);

        // 从 requestParams 中提取非 HEADER 参数
        List<String> requiredFields = new ArrayList<>();
        requiredFields.add("datasourceId");

        if (endpoint.getRequestParams() != null) {
            for (ApiParam param : endpoint.getRequestParams()) {
                // HEADER 参数由 datasourceId 自动解析，不暴露给 Agent
                if ("HEADER".equalsIgnoreCase(param.getParamLocation())) {
                    continue;
                }

                JSONObject prop = new JSONObject(new LinkedHashMap<>());
                prop.set("type", mapApiTypeToJsonType(param.getType()));
                if (param.getDescription() != null) {
                    prop.set("description", param.getDescription());
                }
                if (param.getEnumValues() != null && !param.getEnumValues().isBlank()) {
                    JSONArray enumArr = new JSONArray();
                    for (String ev : param.getEnumValues().split(",")) {
                        enumArr.add(ev.trim());
                    }
                    prop.set("enum", enumArr);
                }
                if (param.getDefaultValue() != null && !param.getDefaultValue().isBlank()) {
                    prop.set("default", parseDefaultValue(param));
                }
                // 数组类型补充 items
                if (param.getType() != null && param.getType().startsWith("Array")) {
                    JSONObject items = new JSONObject(new LinkedHashMap<>());
                    items.set("type", extractArrayItemType(param.getType()));
                    prop.set("items", items);
                }

                // 参数名中的连字符转为下划线，避免 JSON Schema 兼容问题
                String propName = param.getName().replace("-", "_");
                properties.set(propName, prop);

                if (Boolean.TRUE.equals(param.getRequired())) {
                    requiredFields.add(propName);
                }
            }
        }

        schema.set("properties", properties);
        schema.set("required", requiredFields);
        return schema.toString();
    }

    /**
     * 将 ApiParam 类型映射为 JSON Schema 类型
     */
    private String mapApiTypeToJsonType(String apiType) {
        if (apiType == null) {
            return "string";
        }
        if (apiType.startsWith("Array")) {
            return "array";
        }
        return switch (apiType.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "boolean" -> "boolean";
            case "double", "float" -> "number";
            case "map", "object" -> "object";
            default -> "string";
        };
    }

    /**
     * 提取 Array 类型的元素类型
     */
    private String extractArrayItemType(String apiType) {
        if (apiType == null || !apiType.startsWith("Array")) {
            return "string";
        }
        // Array[String] -> string, Array[Object] -> object
        if (apiType.contains("[String]")) {
            return "string";
        } else if (apiType.contains("[Object]")) {
            return "object";
        } else if (apiType.contains("[Integer]")) {
            return "integer";
        }
        return "string";
    }

    /**
     * 将默认值字符串按参数类型转换为对应 Java 类型
     */
    private Object parseDefaultValue(ApiParam param) {
        String defaultVal = param.getDefaultValue();
        String type = param.getType() != null ? param.getType() : "String";
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
            log.warn("参数 [{}] 默认值 '{}' 无法按类型 {} 解析，保留字符串", param.getName(), defaultVal, type);
        }
        return defaultVal;
    }

    /**
     * 通用动态 Tool 调用处理
     * <p>
     * 流程：datasourceId → 校验数据源 → 解析配置 →
     * 构建参数（含认证 Header）→ 查询请求校验与自动修正 → callWithParams → 格式化响应
     * <p>
     * 认证方式：tenant-id / auth-type / auth-value 从数据源配置中获取，
     * 由 AloudataApiClient 自动注入到请求 Header，无需预先获取 Token。
     */
    private String handleDynamicToolCall(String endpointName, String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            Long datasourceId = input.getLong("datasourceId");

            // 解析数据源白名单（含单值自动注入、可用列表引导）
            ChatOrigin dsOrigin = ChatOriginHolder.get();
            String dsConvId = dsOrigin != null ? dsOrigin.conversationId() : null;
            ScopeResolveResult<Long> dsScope = scopeContext.resolveDatasourceId(dsConvId, datasourceId);
            if (dsScope.hasError()) {
                return error(dsScope.getErrorMessage());
            }
            datasourceId = dsScope.getResolvedValue();

            // datasourceId 自动注入强化：白名单为空时尝试自动查找
            if (datasourceId == null && !scopeContext.hasScope(dsConvId)) {
                datasourceId = autoResolveDatasourceId();
                if (datasourceId == null) {
                    return error("需要 datasourceId 参数指定数据源");
                }
            } else if (datasourceId == null) {
                return error("需要 datasourceId 参数指定数据源");
            }

            // 校验数据源
            String validationError = validateAloudataDatasource(datasourceId);
            if (validationError != null) {
                return error(validationError);
            }

            // 解析配置
            DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
            AloudataConfigDTO config = configHelper.parseConfig(entity);

            // 用户查询时必须使用自己的 Aloudata 认证值（auth-value），不允许使用数据源管理员的认证值
            // tenant-id 和 auth-type 仍来自数据源共享配置，仅 auth-value 替换为用户绑定的认证值
            Long currentUserId = UserContextHolder.getUserId();
            if (currentUserId == null) {
                return error("当前用户未登录，无法执行 Aloudata 查询");
            }
            String userAuthValue = datasourceAccountService.resolveAloudataAuthValue(datasourceId, currentUserId);
            if (userAuthValue == null) {
                return error("当前用户未绑定 Aloudata 认证值，请先在数据源页面配置查询账号后再执行查询");
            }
            config.setAuthValue(userAuthValue);
            log.info("用户 {} 使用自定义 Aloudata 认证值访问数据源 {}", currentUserId, datasourceId);

            // 构建参数 Map：从 input 中提取非 datasourceId 的参数
            Map<String, Object> params = buildParamsFromInput(endpointName, input, config);

            // ====== 指标查询端点专属：校验 + 自动修正 ======
            if (METRICS_QUERY_ENDPOINT.equals(endpointName)) {
                // P0: 强制 queryResultType 为 DATA，避免 SQL_AND_DATA 导致数据截断
                params.put("queryResultType", "DATA");

                // P0: timeConstraint 自动规范化
                normalizeTimeConstraint(params);

                // P0+P1: 查询请求校验（中文展示名、同环比约束、占比/排名维度、维度可用性）
                List<String> validationErrors = validateMetricsQueryRequest(datasourceId, params);
                if (!validationErrors.isEmpty()) {
                    return error("查询请求校验失败：\n" + String.join("\n", validationErrors)
                            + "\n请修正后重试。");
                }
            }

            // 调用 API（认证 Header 由 apiClient 自动注入）
            ResponseEntity<Map> response = apiClient.callWithParams(endpointName, config, params);

            // 格式化响应
            return formatResponse(endpointName, response);
        } catch (IllegalArgumentException e) {
            log.warn("Aloudata Tool [{}] 参数校验失败: {}", endpointName, e.getMessage());
            return error(e.getMessage());
        } catch (Exception e) {
            log.error("Aloudata Tool [{}] 调用失败: {}", endpointName, e.getMessage(), e);
            return error("调用失败: " + e.getMessage());
        }
    }

    /**
     * 从 Tool 输入构建 API 调用参数
     * <p>
     * 自动填充 HEADER 参数（从 config 解析），将 Agent 输入参数名中的下划线还原为连字符
     */
    private Map<String, Object> buildParamsFromInput(String endpointName, JSONObject input, AloudataConfigDTO config) {
        Map<String, Object> params = endpointService.buildHeaderParamsFromConfig(endpointName, config);

        // 从 input 中提取非 datasourceId 参数
        ApiEndpoint endpoint = endpointService.getEndpoint(endpointName);
        if (endpoint != null && endpoint.getRequestParams() != null) {
            for (ApiParam paramDef : endpoint.getRequestParams()) {
                if ("HEADER".equalsIgnoreCase(paramDef.getParamLocation())) {
                    continue;
                }
                String paramName = paramDef.getName();
                // 支持下划线和连字符两种形式
                String inputKey = paramName.replace("-", "_");
                Object value = input.get(inputKey);
                if (value == null) {
                    value = input.get(paramName);
                }
                if (value != null) {
                    params.put(paramName, value);
                }
            }
        }

        return params;
    }

    /**
     * 格式化 API 响应为 Agent 可读文本
     * <p>
     * 对指标查询端点增加：API 错误智能解析（修正建议）、空结果诊断信息。
     */
    @SuppressWarnings("unchecked")
    private String formatResponse(String endpointName, ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return error("API 请求失败，HTTP 状态码: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Boolean success = (Boolean) responseBody.get("success");

        if (!Boolean.TRUE.equals(success)) {
            String errorMsg = (String) responseBody.get("errorMsg");
            String detailErrorMsg = (String) responseBody.get("detailErrorMsg");
            String combinedMsg = errorMsg != null ? errorMsg : detailErrorMsg;

            // P1: API 错误智能解析，附加修正建议
            String suggestion = matchErrorSuggestion(combinedMsg);
            if (suggestion != null) {
                return error("API: " + endpointName + " 返回错误: " + combinedMsg
                        + "\n修正建议: " + suggestion);
            }
            return error("API: " + endpointName + " 返回错误: " + combinedMsg);
        }

        // 通用 JSON 格式化
        Object data = responseBody.get("data");
        if (data == null) {
            return JSONUtil.toJsonStr(new JSONObject(new LinkedHashMap<>()).set("success", true).set("data", null));
        }

        // P1: 指标查询端点 — 空结果诊断 + P2: 列式转行式
        if (METRICS_QUERY_ENDPOINT.equals(endpointName) && data instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object tableObj = dataMap.get("table");
            if (tableObj instanceof Map) {
                Map<String, Object> table = (Map<String, Object>) tableObj;

                // P2: 列式数据转行式数据，便于 LLM 理解
                Object columnsObj = table.get("columns");
                Object rowsObj = table.get("rows");
                if (columnsObj instanceof Map && (rowsObj == null || (rowsObj instanceof List && ((List<?>) rowsObj).isEmpty()))) {
                    List<Map<String, Object>> convertedRows = convertColumnarToRows((Map<String, Object>) columnsObj);
                    if (!convertedRows.isEmpty()) {
                        table.remove("columns"); // 移除列式数据，减少返回体积
                        table.put("rows", convertedRows);
                    }
                }

                // 空结果诊断
                Object finalRowsObj = table.get("rows");
                boolean isEmptyRows = finalRowsObj == null
                        || (finalRowsObj instanceof List && ((List<?>) finalRowsObj).isEmpty());
                if (isEmptyRows) {
                    JSONObject result = new JSONObject(new LinkedHashMap<>());
                    result.set("success", true);
                    result.set("data", data);
                    result.set("diagnosis", Map.of(
                            "isEmptyResult", true,
                            "suggestion", "查询返回0条数据，可能原因：1) timeConstraint范围无数据 2) filters条件过滤掉了所有数据 3) 指标在该数据源下无数据。建议：尝试放宽时间范围或移除筛选条件重试。"
                    ));
                    return result.toStringPretty();
                }
            }
        }

        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.set("success", true);
        result.set("data", data);
        return result.toStringPretty();
    }

    /**
     * 处理语义搜索 Tool 调用
     * <p>
     * 对于 Aloudata 数据源：使用 AloudataSemanticEsService 进行指标+维度级检索，
     * 直接命中指标名和维度名，用于构造 metrics_query 请求。
     * 对于非 Aloudata 数据源：保持原有表级检索逻辑（SchemaEmbeddingService）。
     */
    private String handleSearchSemantic(String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            Long datasourceId = input.getLong("datasourceId");

            // 解析数据源白名单（含单值自动注入、可用列表引导）
            ChatOrigin dsOrigin = ChatOriginHolder.get();
            String dsConvId = dsOrigin != null ? dsOrigin.conversationId() : null;
            ScopeResolveResult<Long> dsScope = scopeContext.resolveDatasourceId(dsConvId, datasourceId);
            if (dsScope.hasError()) {
                return error(dsScope.getErrorMessage());
            }
            datasourceId = dsScope.getResolvedValue();
            if (datasourceId == null) {
                return error("需要 datasourceId 参数");
            }
            String keyword = input.getStr("keyword");
            if (keyword == null || keyword.isBlank()) {
                return error("需要 keyword 参数");
            }

            int topK = input.getInt("topK", DataAgentConstants.ALOUDATA_SEARCH_DEFAULT_TOP_K);
            double threshold = input.getDouble("similarityThreshold",
                    DataAgentConstants.ALOUDATA_SEARCH_DEFAULT_THRESHOLD);

            // 判断数据源类型，选择检索路径
            DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
            if (entity == null) {
                return error("数据源不存在, id=" + datasourceId);
            }

            if ("aloudata".equalsIgnoreCase(entity.getSourceType())) {
                // Aloudata 数据源：指标+维度级语义检索
                return handleAloudataSemanticSearch(datasourceId, keyword, topK, threshold);
            } else {
                // 非 Aloudata 数据源：保持原有表级检索
                return handleGenericSemanticSearch(datasourceId, keyword, topK, threshold);
            }
        } catch (Exception e) {
            log.error("语义搜索失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * 处理业务术语检索 Tool 调用
     */
    private String handleSearchBusinessTerm(String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            String keyword = input.getStr("keyword");
            if (keyword == null || keyword.isBlank()) {
                return error("需要 keyword 参数");
            }

            int topK = input.getInt("topK", DataAgentConstants.BUSINESS_TERM_SEARCH_DEFAULT_TOP_K);
            double threshold = input.getDouble("similarityThreshold",
                    DataAgentConstants.BUSINESS_TERM_SEARCH_DEFAULT_THRESHOLD);

            BusinessTermSearchResult searchResult = businessTermEsService.hybridSearch(keyword, topK, threshold);

            StringBuilder sb = new StringBuilder();
            sb.append("**搜索关键词**: ").append(keyword).append("\n");

            int hitCount = searchResult.getTermHits() != null ? searchResult.getTermHits().size() : 0;
            sb.append("**匹配结果**: ").append(hitCount).append(" 个术语");
            sb.append(" (检索耗时: ").append(searchResult.getElapsedMs()).append("ms)\n\n");

            if (searchResult.getTermHits() != null && !searchResult.getTermHits().isEmpty()) {
                sb.append("## 术语匹配\n\n");
                for (BusinessTermSearchResult.TermHit hit : searchResult.getTermHits()) {
                    sb.append("- **").append(hit.getTermName()).append("**");
                    if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
                        sb.append("（同义词: ").append(hit.getSynonyms()).append("）");
                    }
                    if (hit.getDescription() != null && !hit.getDescription().isBlank()) {
                        sb.append(" - ").append(hit.getDescription());
                    }
                    if (hit.getCategory() != null && !hit.getCategory().isBlank()) {
                        sb.append(" [分类: ").append(hit.getCategory()).append("]");
                    }
                    sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                    sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n");
                }
                sb.append("\n");
            }

            if (hitCount == 0) {
                sb.append("未找到匹配的业务术语。请尝试更换关键词。");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("业务术语搜索失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /**
     * Aloudata 数据源的指标+维度级语义检索
     * <p>
     * 优先使用新版语义层检索（AloudataSemanticEsService，指标+维度粒度 + ES + 向量），
     * 若新版语义层未同步，降级到旧版表级检索（SchemaEmbeddingService）。
     * <p>
     * 增强功能：
     * <ul>
     *   <li>自动先查业务术语扩展关键词，提高检索召回率</li>
     *   <li>维度命中时补充关联指标信息</li>
     *   <li>检索无结果时自动降级到全量指标列表</li>
     * </ul>
     */
    private String handleAloudataSemanticSearch(Long datasourceId, String keyword, int topK, double threshold) {
        /* 先检查是否已同步新版语义层 */
        var syncStatus = aloudataSemanticSyncService.getSyncStatus(datasourceId);
        if (!"completed".equals(syncStatus.status())) {
            /* 新版语义层未同步，降级到旧版表级检索 */
            log.info("Aloudata 数据源 [{}] 新版语义层未同步，降级到旧版表级检索", datasourceId);
            return handleGenericSemanticSearch(datasourceId, keyword, topK, threshold);
        }

        /* P1: 自动先查业务术语，扩展检索关键词 */
        List<String> expandedKeywords = expandKeywordsFromBusinessTerms(keyword);

        /* 用扩展关键词分别检索，合并去重 */
        Set<String> seenMetricNames = new LinkedHashSet<>();
        List<AloudataSearchResult.MetricHit> mergedMetrics = new ArrayList<>();
        Set<String> seenDimNames = new LinkedHashSet<>();
        List<AloudataSearchResult.DimensionHit> mergedDimensions = new ArrayList<>();

        for (String kw : expandedKeywords) {
            AloudataSearchResult sr = aloudataSemanticEsService.hybridSearch(datasourceId, kw, topK, threshold);
            if (sr.getMetricHits() != null) {
                for (var hit : sr.getMetricHits()) {
                    if (seenMetricNames.add(hit.getMetricName())) {
                        mergedMetrics.add(hit);
                    }
                }
            }
            if (sr.getDimensionHits() != null) {
                for (var hit : sr.getDimensionHits()) {
                    if (seenDimNames.add(hit.getDimName())) {
                        mergedDimensions.add(hit);
                    }
                }
            }
        }

        /* 按分数重排 */
        mergedMetrics.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        mergedDimensions.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        /* 限制总数 */
        if (mergedMetrics.size() > topK) {
            mergedMetrics = mergedMetrics.subList(0, topK);
        }
        if (mergedDimensions.size() > topK) {
            mergedDimensions = mergedDimensions.subList(0, topK);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**搜索关键词**: ").append(keyword);
        if (expandedKeywords.size() > 1) {
            sb.append("（已扩展: ").append(String.join(", ", expandedKeywords.subList(1, expandedKeywords.size()))).append(")");
        }
        sb.append("\n");
        sb.append("**数据源 ID**: ").append(datasourceId).append("\n");

        int metricCount = mergedMetrics.size();
        int dimensionCount = mergedDimensions.size();
        sb.append("**匹配结果**: ").append(metricCount).append(" 个指标 + ").append(dimensionCount).append(" 个维度\n\n");

        /* 展示指标命中 */
        if (!mergedMetrics.isEmpty()) {
            sb.append("## 指标\n\n");
            for (AloudataSearchResult.MetricHit hit : mergedMetrics) {
                sb.append("- ").append(hit.getPromptInfo());
                sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n");
            }
            sb.append("\n");
        }

        /* 展示维度命中 + P2: 维度-指标关联推荐 */
        if (!mergedDimensions.isEmpty()) {
            sb.append("## 维度\n\n");
            for (AloudataSearchResult.DimensionHit hit : mergedDimensions) {
                sb.append("- ").append(hit.getPromptInfo());
                sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                sb.append(", 来源: ").append(hit.getMatchSource()).append("]");

                // 补充关联指标信息
                List<String> relatedMetrics = findRelatedMetrics(datasourceId, hit.getDimName());
                if (!relatedMetrics.isEmpty()) {
                    sb.append(" → 关联指标: ").append(String.join(", ", relatedMetrics));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        /* P3: 检索失败自动降级 */
        if (metricCount == 0 && dimensionCount == 0) {
            sb.append("语义检索未命中。");
            // 自动降级：查询全量指标概要
            List<AloudataMetricEntity> allMetrics = metricMapper.selectList(
                    new LambdaQueryWrapper<AloudataMetricEntity>()
                            .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                            .select(AloudataMetricEntity::getMetricName,
                                    AloudataMetricEntity::getMetricDisplayName)
                            .last("LIMIT 50"));
            if (!allMetrics.isEmpty()) {
                sb.append("该数据源下有以下指标可供参考：\n");
                for (var m : allMetrics) {
                    sb.append("- ").append(m.getMetricName()).append("(")
                            .append(m.getMetricDisplayName() != null ? m.getMetricDisplayName() : "").append(")\n");
                }
                sb.append("请尝试使用上述指标的英文名(metricName)重新检索。");
            } else {
                sb.append("请尝试使用 aloudata_metrics_list 或 aloudata_dimensions_list 查看所有可用项。");
            }
        }

        return sb.toString();
    }

    /**
     * 非 Aloudata 数据源的通用表级语义检索（保持原有逻辑）
     */
    private String handleGenericSemanticSearch(Long datasourceId, String keyword, int topK, double threshold) {
        String validationError = validateDatasourceAccessible(datasourceId);
        if (validationError != null) {
            return error(validationError);
        }

        SchemaSearchRequest request = new SchemaSearchRequest();
        request.setDatasourceId(datasourceId);
        request.setQuery(keyword);
        request.setTopK(topK);
        request.setSimilarityThreshold(threshold);

        SchemaSearchResult searchResult = schemaEmbeddingService.searchSchema(request);

        StringBuilder sb = new StringBuilder();
        sb.append("**搜索关键词**: ").append(keyword).append("\n");
        sb.append("**数据源 ID**: ").append(datasourceId).append("\n");
        sb.append("**匹配结果**: ").append(searchResult.getTableHits().size()).append(" 个表/分组");
        sb.append(" (检索耗时: ").append(searchResult.getElapsedMs()).append("ms)\n\n");

        if (searchResult.getTableHits().isEmpty()) {
            sb.append("未找到匹配的表。请尝试使用 list_tables 查看所有可用表。");
            return sb.toString();
        }

        for (SchemaSearchResult.TableHit hit : searchResult.getTableHits()) {
            sb.append("### ").append(hit.getTableName());
            if (hit.getTableComment() != null && !hit.getTableComment().isBlank()) {
                sb.append(" - ").append(hit.getTableComment());
            }
            sb.append(" [匹配分数: ").append(String.format("%.3f", hit.getScore()));
            sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n\n");

            if (hit.getSemanticFields() != null && !hit.getSemanticFields().isEmpty()) {
                for (SemanticModelVO vo : hit.getSemanticFields()) {
                    sb.append("- ").append(vo.getPromptInfo()).append("\n");
                }
                sb.append("\n");
            }
        }

        if (searchResult.getRelations() != null && !searchResult.getRelations().isEmpty()) {
            sb.append("## 关联关系\n\n");
            for (LogicalRelationVO rel : searchResult.getRelations()) {
                sb.append("- ").append(rel.getPromptInfo()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 校验数据源是否可访问（通用，不限 Aloudata 类型）
     */
    private String validateDatasourceAccessible(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            return "数据源不存在, id=" + datasourceId;
        }
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            return "数据源已禁用, id=" + datasourceId;
        }
        return null;
    }

    /**
     * 校验数据源是否为 Aloudata 类型
     *
     * @return 错误信息，null 表示校验通过
     */
    private String validateAloudataDatasource(Long datasourceId) {
        DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            return "数据源不存在, id=" + datasourceId;
        }
        if (!"aloudata".equalsIgnoreCase(entity.getSourceType())) {
            return "数据源类型不是 aloudata, 当前类型: " + entity.getSourceType();
        }
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            return "数据源已禁用, id=" + datasourceId;
        }
        return null;
    }

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }

    // ==================== 查询请求校验与自动修正 ====================

    /**
     * P0: timeConstraint 自动规范化
     * <p>
     * 1. 确保外层有括号包裹
     * 2. 替换 BETWEEN ... AND ... 为 >= AND <=
     * 3. 确保未用方括号的 metric_time 引用加上方括号
     */
    private void normalizeTimeConstraint(Map<String, Object> params) {
        Object tcObj = params.get("timeConstraint");
        if (tcObj == null) {
            return;
        }
        String tc = tcObj.toString().trim();
        if (tc.isEmpty()) {
            return;
        }

        // 1. 替换 BETWEEN ... AND ... 为 >= AND <=
        // 匹配: [metric_time__day] BETWEEN "2024-01-01" AND "2024-01-31"
        tc = tc.replaceAll(
                "\\[metric_time__(\\w+)\\]\\s*[Bb][Ee][Tt][Ww][Ee][Ee][Nn]\\s*\"([^\"]+)\"\\s+[Aa][Nn][Dd]\\s*\"([^\"]+)\"",
                "[metric_time__$1]>=\"$2\" AND [metric_time__$1]<=\"$3\""
        );

        // 2. 确保外层括号
        if (!tc.startsWith("(") || !tc.endsWith(")")) {
            tc = "(" + tc + ")";
        }

        // 3. 修复未用方括号的 metric_time 引用（如 metric_time__day → [metric_time__day]）
        // 仅在非方括号内的 metric_time 引用
        tc = tc.replaceAll("(?<!\\[)(metric_time__(?:day|month|year|week|quarter))(?![\\]\\w])", "[$1]");

        if (!tc.equals(tcObj.toString())) {
            log.info("timeConstraint 自动规范化: {} -> {}", tcObj, tc);
            params.put("timeConstraint", tc);
        }
    }

    /**
     * P0+P1: 指标查询请求校验
     * <p>
     * 校验项：中文展示名、同环比约束、占比/排名维度声明、维度可用性、filters格式、orders字段。
     * 校验失败直接返回错误列表，阻止错误请求打到远端 API。
     *
     * @param datasourceId 数据源ID，用于维度可用性校验
     * @param params       查询请求参数
     * @return 错误列表，空列表表示校验通过
     */
    @SuppressWarnings("unchecked")
    private List<String> validateMetricsQueryRequest(Long datasourceId, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();

        List<String> metrics = params.get("metrics") instanceof List
                ? (List<String>) params.get("metrics") : List.of();
        List<String> dimensions = params.get("dimensions") instanceof List
                ? (List<String>) params.get("dimensions") : List.of();
        String tc = params.get("timeConstraint") instanceof String
                ? (String) params.get("timeConstraint") : "";

        // 1. metrics 非空
        if (metrics.isEmpty()) {
            errors.add("metrics 参数为空，至少需要一个指标");
            return errors;
        }

        // 2. 中文展示名检查
        for (String m : metrics) {
            String base = m.split("__")[0];
            if (base.matches(".*[\\u4e00-\\u9fff].*")) {
                errors.add("指标 '" + base + "' 使用了中文展示名，请使用英文名(metricName)");
            }
        }
        for (String d : dimensions) {
            String base = d.contains("__") ? d.split("__")[0] : d;
            if (!"metric_time".equals(base) && base.matches(".*[\\u4e00-\\u9fff].*")) {
                errors.add("维度 '" + base + "' 使用了中文展示名，请使用英文名(dimName)");
            }
        }

        // 3. 同环比约束检查
        boolean hasTimeGrain = dimensions.stream().anyMatch(d -> d.startsWith("metric_time__"));
        boolean hasTimeInTc = tc.contains("metric_time");

        for (String m : metrics) {
            if (m.contains("__sameperiod__")) {
                // 时间维度必须存在
                if (!hasTimeGrain && !hasTimeInTc) {
                    errors.add("同环比指标 '" + m + "' 缺少时间维度，metric_time 必须在 dimensions 或 timeConstraint 中");
                }
                // 偏移粒度不可小于日期粒度
                String offsetGrain = extractOffsetGrain(m);
                String timeGrain = extractTimeGrain(dimensions);
                if (offsetGrain != null && timeGrain != null) {
                    int offsetLevel = GRAIN_LEVEL.getOrDefault(offsetGrain, 0);
                    int dimLevel = GRAIN_LEVEL.getOrDefault(timeGrain, 0);
                    if (offsetLevel < dimLevel) {
                        errors.add("同环比指标 '" + m + "' 的偏移粒度(" + offsetGrain + ")小于日期粒度(" + timeGrain + ")，偏移粒度不可小于日期粒度");
                    }
                }
            }

            // 4. 占比维度约束
            if (m.contains("__proportion__")) {
                List<String> propDims = extractCalcDims(m, "proportion");
                for (String pd : propDims) {
                    if (!dimensions.contains(pd)) {
                        errors.add("占比范围维度 '" + pd + "' 未在 dimensions 中声明");
                    }
                }
            }

            // 5. 排名维度约束
            if (m.contains("__rank__")) {
                List<String> rankDims = extractCalcDims(m, "rank");
                for (String rd : rankDims) {
                    if (!dimensions.contains(rd)) {
                        errors.add("排名范围维度 '" + rd + "' 未在 dimensions 中声明");
                    }
                }
            }
        }

        // 6. timeConstraint 格式检查
        if (!tc.isEmpty()) {
            if (!tc.startsWith("(") || !tc.endsWith(")")) {
                errors.add("timeConstraint 格式错误: 整个表达式必须用 () 包裹");
            }
        }

        // 7. filters 维度引用格式检查
        List<String> filters = params.get("filters") instanceof List
                ? (List<String>) params.get("filters") : List.of();
        for (String f : filters) {
            if (!f.startsWith("[")) {
                errors.add("筛选条件 '" + f + "' 格式不正确，维度引用应使用方括号，如 [region] IN (\"华东\")");
            }
        }

        // 8. orders 字段检查
        List<Map<String, String>> orders = params.get("orders") instanceof List
                ? (List<Map<String, String>>) params.get("orders") : List.of();
        Set<String> validOrderFields = new HashSet<>();
        metrics.forEach(m -> validOrderFields.add(m.split("__")[0]));
        validOrderFields.addAll(dimensions);
        for (Map<String, String> order : orders) {
            for (String field : order.keySet()) {
                if (!validOrderFields.contains(field)) {
                    errors.add("排序字段 '" + field + "' 不在 metrics 或 dimensions 中");
                }
            }
        }

        // 9. P1: 维度可用性硬校验
        List<String> dimErrors = validateDimensionAvailability(datasourceId, metrics, dimensions);
        errors.addAll(dimErrors);

        return errors;
    }

    /**
     * P1: 维度可用性硬校验
     * <p>
     * 检查 dimensions 中的用户维度是否在指标的可用维度集中。
     * metric_time 系列维度为系统维度，始终可用，跳过校验。
     */
    private List<String> validateDimensionAvailability(Long datasourceId, List<String> metrics, List<String> dimensions) {
        List<String> errors = new ArrayList<>();
        if (metrics.isEmpty() || dimensions.isEmpty()) {
            return errors;
        }

        // 过滤掉 metric_time 系列维度（系统维度，始终可用）
        List<String> userDims = dimensions.stream()
                .filter(d -> !d.startsWith("metric_time"))
                .toList();
        if (userDims.isEmpty()) {
            return errors;
        }

        // 提取指标基础名（去掉快速计算后缀）
        List<String> baseMetricNames = metrics.stream()
                .map(m -> m.split("__")[0])
                .distinct()
                .toList();

        try {
            // 查询指标-维度关联关系
            LambdaQueryWrapper<AloudataMetricDimensionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId);
            wrapper.in(AloudataMetricDimensionEntity::getMetricName, baseMetricNames);
            List<AloudataMetricDimensionEntity> relations = metricDimensionMapper.selectList(wrapper);

            // 每个 metric 的可用维度集合
            Map<String, Set<String>> metricDimSet = relations.stream()
                    .collect(Collectors.groupingBy(
                            AloudataMetricDimensionEntity::getMetricName,
                            Collectors.mapping(AloudataMetricDimensionEntity::getDimName, Collectors.toSet())
                    ));

            for (String dim : userDims) {
                for (Map.Entry<String, Set<String>> entry : metricDimSet.entrySet()) {
                    if (!entry.getValue().contains(dim)) {
                        errors.add("维度 '" + dim + "' 不在指标 '" + entry.getKey() + "' 的可用维度集中，使用该维度将导致查询报错");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("维度可用性校验查询失败，跳过校验: {}", e.getMessage());
        }

        return errors;
    }

    /**
     * 从同环比快速计算语法中提取偏移粒度
     * <p>
     * 示例: "sales_amount__sameperiod__yoy__growth" → "year"
     */
    private String extractOffsetGrain(String metricName) {
        // 匹配 __sameperiod__{可选N_}grain__ 或 __sameperiod__{可选N_}grain__flag__
        Pattern pattern = Pattern.compile("__sameperiod__(?:-?\\d+_)?(\\w+?)(?:__\\w+)?__(?:value|growthvalue|growth|decrease|decreaserate)$");
        Matcher matcher = pattern.matcher(metricName);
        if (matcher.find()) {
            String grain = matcher.group(1);
            return OFFSET_GRAIN_MAP.getOrDefault(grain, grain);
        }
        return null;
    }

    /**
     * 从 dimensions 列表中提取时间粒度
     * <p>
     * 示例: ["region", "metric_time__month"] → "month"
     */
    private String extractTimeGrain(List<String> dimensions) {
        for (String dim : dimensions) {
            if (dim.startsWith("metric_time__")) {
                return dim.replace("metric_time__", "");
            }
            if ("metric_time".equals(dim)) {
                return "day";
            }
        }
        return null;
    }

    /**
     * 从占比/排名快速计算语法中提取范围维度
     * <p>
     * 示例: "sales_amount__proportion__region,province" → ["region", "province"]
     */
    private List<String> extractCalcDims(String metricName, String calcType) {
        Pattern pattern = Pattern.compile("__" + calcType + "__(.+)$");
        Matcher matcher = pattern.matcher(metricName);
        if (matcher.find()) {
            return Arrays.stream(matcher.group(1).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of();
    }

    // ==================== 语义检索增强 ====================

    /**
     * P1: 自动先查业务术语，扩展检索关键词
     * <p>
     * 将术语名和同义词作为扩展关键词，提高检索召回率。
     * 限制扩展数量避免过多检索调用。
     */
    private List<String> expandKeywordsFromBusinessTerms(String keyword) {
        List<String> keywords = new ArrayList<>();
        keywords.add(keyword);

        try {
            BusinessTermSearchResult termResult = businessTermEsService.hybridSearch(keyword, 3, 0.3);
            if (termResult.getTermHits() != null) {
                for (var hit : termResult.getTermHits()) {
                    // 添加术语标准名
                    if (hit.getTermName() != null && !hit.getTermName().isBlank()) {
                        keywords.add(hit.getTermName());
                    }
                    // 添加同义词
                    if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
                        for (String syn : hit.getSynonyms().split(",")) {
                            String trimmed = syn.trim();
                            if (!trimmed.isEmpty() && !trimmed.equals(keyword)) {
                                keywords.add(trimmed);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("业务术语扩展检索失败，使用原始关键词: {}", e.getMessage());
        }

        // 去重并限制最多5个关键词，避免过多检索调用
        return keywords.stream().distinct().limit(5).toList();
    }

    /**
     * P2: 查找维度关联的指标
     * <p>
     * 用于语义检索结果中为维度补充关联指标信息，帮助 LLM 正确配对指标和维度。
     */
    private List<String> findRelatedMetrics(Long datasourceId, String dimName) {
        if (dimName == null) {
            return List.of();
        }
        try {
            LambdaQueryWrapper<AloudataMetricDimensionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId);
            wrapper.eq(AloudataMetricDimensionEntity::getDimName, dimName);
            wrapper.select(AloudataMetricDimensionEntity::getMetricName);
            wrapper.last("LIMIT 5");
            List<AloudataMetricDimensionEntity> assocMetrics = metricDimensionMapper.selectList(wrapper);
            return assocMetrics.stream()
                    .map(AloudataMetricDimensionEntity::getMetricName)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.debug("查询维度关联指标失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== datasourceId 自动注入 ====================

    /**
     * P2: datasourceId 自动注入强化
     * <p>
     * 当白名单为空且 LLM 未传 datasourceId 时，自动查找用户可用的 Aloudata 数据源。
     * 只有一个数据源时自动注入，多个时返回列表引导。
     */
    private Long autoResolveDatasourceId() {
        try {
            List<DatasourceEntity> aloudataSources = datasourceMapper.selectList(
                    new LambdaQueryWrapper<DatasourceEntity>()
                            .eq(DatasourceEntity::getSourceType, DataAgentConstants.SOURCE_TYPE_ALOUDATA)
                            .eq(DatasourceEntity::getEnabled, true)
                            .select(DatasourceEntity::getId, DatasourceEntity::getName));
            if (aloudataSources.size() == 1) {
                Long id = aloudataSources.get(0).getId();
                log.info("自动注入唯一 Aloudata 数据源 ID: {}", id);
                return id;
            }
        } catch (Exception e) {
            log.warn("自动查找 Aloudata 数据源失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== API 错误智能解析 ====================

    /**
     * P1: API 错误智能解析，匹配常见错误模式并返回修正建议
     */
    private String matchErrorSuggestion(String errorMsg) {
        if (errorMsg == null) {
            return null;
        }
        String lower = errorMsg.toLowerCase();

        if (lower.contains("dimension") && (lower.contains("not available") || lower.contains("not support") || lower.contains("不可用"))) {
            return "使用了不可用的维度。请先调用 aloudata_metric_available_dimensions 确认指标的可用维度，移除不支持的维度后重试。";
        }
        if (lower.contains("timeconstraint") || lower.contains("time constraint") || lower.contains("时间约束")) {
            return "timeConstraint 格式错误。请确保：1)整个表达式用()包裹 2)metric_time用[]引用 3)日期值用双引号包裹 4)不支持BETWEEN语法，改用>= AND <=。";
        }
        if (lower.contains("sameperiod") || lower.contains("同比") || lower.contains("环比") || lower.contains("offset")) {
            return "同环比计算错误。请确保：1)metric_time在dimensions或timeConstraint中 2)timeConstraint中为单值筛选 3)偏移粒度不小于日期粒度（月粒度下不可用日环比）。";
        }
        if (lower.contains("metric") && (lower.contains("not found") || lower.contains("不存在") || lower.contains("not exist"))) {
            return "指标不存在。请先调用 aloudata_search_semantic 确认指标的英文名(metricName)，不要使用中文展示名。";
        }
        if (lower.contains("proportion") || lower.contains("占比")) {
            return "占比计算错误。请确保占比范围维度已在 dimensions 中声明。";
        }
        if (lower.contains("rank") || lower.contains("排名")) {
            return "排名计算错误。请确保排名范围维度已在 dimensions 中声明。";
        }
        if (lower.contains("filter") || lower.contains("筛选")) {
            return "筛选条件格式错误。请确保：维度用方括号引用如[region]，字符串值用双引号包裹如\"华东\"。";
        }
        return null;
    }

    // ==================== 查询结果格式化 ====================

    /**
     * P2: 列式数据转行式数据
     * <p>
     * Aloudata API 返回的 columns 格式为 {colName: [{value:..., flag:..., count:...}, ...]}，
     * LLM 理解行式数据更直观。此方法将列式数据转为 [{colName: value, ...}, ...] 格式。
     * 仅提取 value 字段，丢弃 flag 和 count。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertColumnarToRows(Map<String, Object> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }

        // 确定行数（取第一个列的长度）
        int rowCount = 0;
        String firstCol = null;
        for (Map.Entry<String, Object> entry : columns.entrySet()) {
            if (entry.getValue() instanceof List) {
                List<?> colValues = (List<?>) entry.getValue();
                if (!colValues.isEmpty()) {
                    rowCount = colValues.size();
                    firstCol = entry.getKey();
                    break;
                }
            }
        }
        if (rowCount == 0 || firstCol == null) {
            return List.of();
        }

        // 转换
        List<Map<String, Object>> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : columns.entrySet()) {
                String colName = entry.getKey();
                if (entry.getValue() instanceof List) {
                    List<?> colValues = (List<?>) entry.getValue();
                    if (i < colValues.size()) {
                        Object cell = colValues.get(i);
                        // 提取 value 字段（ColumnValue 格式）
                        if (cell instanceof Map) {
                            row.put(colName, ((Map<String, Object>) cell).get("value"));
                        } else {
                            row.put(colName, cell);
                        }
                    }
                }
            }
            rows.add(row);
        }
        return rows;
    }
}
