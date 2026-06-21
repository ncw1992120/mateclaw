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
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.*;
import vip.mate.dataagent.model.DatasourceEntity;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.AloudataSemanticEsService;
import vip.mate.dataagent.service.AloudataSemanticSyncService;
import vip.mate.dataagent.service.SchemaEmbeddingService;
import vip.mate.dataagent.service.SemanticModelService;
import vip.mate.dataagent.support.DataAgentChatScopeContext;
import vip.mate.dataagent.util.JdbcUtils;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final DatasourceMapper datasourceMapper;
    private final MateClawRuntime mateClawRuntime;
    private final DataAgentChatScopeContext scopeContext;

    /** 指标查询端点名，需要 ECharts 图表生成等增值逻辑 */
    private static final String METRICS_QUERY_ENDPOINT = "metrics_query";

    /** Tool 名称前缀 */
    private static final String TOOL_PREFIX = "aloudata_";

    /** 语义搜索 Tool 名称 */
    private static final String SEARCH_SEMANTIC_TOOL_NAME = "aloudata_search_semantic";

    /** Markdown 表格展示阈值 */
    private static final int MARKDOWN_TABLE_THRESHOLD = 20;

    /** Markdown 表格最大列数 */
    private static final int MAX_COLUMNS_FOR_TABLE = 10;

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

        log.info("Aloudata 动态 Tool 注册完成，共注册 {} 个 API Tool + 1 个语义搜索 Tool", registeredCount);
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
     * 构建参数（含认证 Header）→ callWithParams → 格式化响应
     * <p>
     * 认证方式：tenant-id / auth-type / auth-value 从数据源配置中获取，
     * 由 AloudataApiClient 自动注入到请求 Header，无需预先获取 Token。
     */
    private String handleDynamicToolCall(String endpointName, String toolInput) {
        try {
            JSONObject input = JSONUtil.parseObj(toolInput);
            Long datasourceId = input.getLong("datasourceId");
            if (datasourceId == null) {
                return error("需要 datasourceId 参数指定数据源");
            }

            // 校验数据源白名单
            String scopeDenyMsg = checkDatasourceScope(datasourceId);
            if (scopeDenyMsg != null) {
                return error(scopeDenyMsg);
            }

            // 校验数据源
            String validationError = validateAloudataDatasource(datasourceId);
            if (validationError != null) {
                return error(validationError);
            }

            // 解析配置
            DatasourceEntity entity = datasourceMapper.selectById(datasourceId);
            AloudataConfigDTO config = configHelper.parseConfig(entity);

            // 构建参数 Map：从 input 中提取非 datasourceId 的参数
            Map<String, Object> params = buildParamsFromInput(endpointName, input, config);

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
     */
    private String formatResponse(String endpointName, ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return error("API 请求失败，HTTP 状态码: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Boolean success = (Boolean) responseBody.get("success");

        if (!Boolean.TRUE.equals(success)) {
            String errorMsg = (String) responseBody.get("errorMsg");
            String detailErrorMsg = (String) responseBody.get("detailErrorMsg");
            return error("API: " + endpointName + " 返回错误: " + (errorMsg != null ? errorMsg : detailErrorMsg));
        }

        // 通用 JSON 格式化
        Object data = responseBody.get("data");
        if (data == null) {
            return JSONUtil.toJsonStr(new JSONObject(new LinkedHashMap<>()).set("success", true).set("data", null));
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
            if (datasourceId == null) {
                return error("需要 datasourceId 参数");
            }
            String keyword = input.getStr("keyword");
            if (keyword == null || keyword.isBlank()) {
                return error("需要 keyword 参数");
            }

            // 校验数据源白名单
            String scopeDenyMsg = checkDatasourceScope(datasourceId);
            if (scopeDenyMsg != null) {
                return error(scopeDenyMsg);
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
     * Aloudata 数据源的指标+维度级语义检索
     * <p>
     * 优先使用新版语义层检索（AloudataSemanticEsService，指标+维度粒度 + ES + 向量），
     * 若新版语义层未同步，降级到旧版表级检索（SchemaEmbeddingService）。
     */
    private String handleAloudataSemanticSearch(Long datasourceId, String keyword, int topK, double threshold) {
        /* 先检查是否已同步新版语义层 */
        var syncStatus = aloudataSemanticSyncService.getSyncStatus(datasourceId);
        if ("completed".equals(syncStatus.status())) {
            /* 新版语义层已同步，使用指标+维度级检索 */
            AloudataSearchResult searchResult = aloudataSemanticEsService.hybridSearch(datasourceId, keyword, topK, threshold);

            StringBuilder sb = new StringBuilder();
            sb.append("**搜索关键词**: ").append(keyword).append("\n");
            sb.append("**数据源 ID**: ").append(datasourceId).append("\n");

            int metricCount = searchResult.getMetricHits() != null ? searchResult.getMetricHits().size() : 0;
            int dimensionCount = searchResult.getDimensionHits() != null ? searchResult.getDimensionHits().size() : 0;
            sb.append("**匹配结果**: ").append(metricCount).append(" 个指标 + ").append(dimensionCount).append(" 个维度");
            sb.append(" (检索耗时: ").append(searchResult.getElapsedMs()).append("ms)\n\n");

            /* 展示指标命中 */
            if (searchResult.getMetricHits() != null && !searchResult.getMetricHits().isEmpty()) {
                sb.append("## 指标\n\n");
                for (AloudataSearchResult.MetricHit hit : searchResult.getMetricHits()) {
                    sb.append("- ").append(hit.getPromptInfo());
                    sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                    sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n");
                }
                sb.append("\n");
            }

            /* 展示维度命中 */
            if (searchResult.getDimensionHits() != null && !searchResult.getDimensionHits().isEmpty()) {
                sb.append("## 维度\n\n");
                for (AloudataSearchResult.DimensionHit hit : searchResult.getDimensionHits()) {
                    sb.append("- ").append(hit.getPromptInfo());
                    sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                    sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n");
                }
                sb.append("\n");
            }

            if (metricCount == 0 && dimensionCount == 0) {
                sb.append("未找到匹配的指标或维度。请尝试使用 aloudata_metrics_list 或 aloudata_dimensions_list 查看所有可用项。");
            }

            return sb.toString();
        }

        /* 新版语义层未同步，降级到旧版表级检索 */
        log.info("Aloudata 数据源 [{}] 新版语义层未同步，降级到旧版表级检索", datasourceId);
        return handleGenericSemanticSearch(datasourceId, keyword, topK, threshold);
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
        if (entity.getEnabled() == null || !entity.getEnabled()) {
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
        if (entity.getEnabled() == null || !entity.getEnabled()) {
            return "数据源已禁用, id=" + datasourceId;
        }
        return null;
    }

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }

    /**
     * 读取当前会话的数据源白名单。
     *
     * @see DatasourceQueryTool#currentDatasourceWhitelist()
     */
    private Set<Long> currentDatasourceWhitelist() {
        ChatOrigin origin = ChatOriginHolder.get();
        if (origin == null || origin.conversationId() == null || origin.conversationId().isBlank()) {
            return Set.of();
        }
        return scopeContext.getAllowedDatasourceIds(origin.conversationId());
    }

    /**
     * 校验 datasourceId 是否在用户勾选的白名单内。
     * <p>
     * 与 {@link DatasourceQueryTool#checkDatasourceAllowed(Long)} 逻辑一致：
     * 白名单为空时不做约束，白名单非空时必须匹配。
     *
     * @param datasourceId 工具调用传入的数据源 ID
     * @return 不在白名单时返回拒绝原因；允许访问时返回 null
     */
    private String checkDatasourceScope(Long datasourceId) {
        Set<Long> allowed = currentDatasourceWhitelist();
        if (allowed.isEmpty()) {
            return null;
        }
        if (datasourceId == null || !allowed.contains(datasourceId)) {
            return "数据源 " + datasourceId + " 不在用户勾选的白名单内，禁止访问。允许的数据源ID：" + allowed;
        }
        return null;
    }
}
