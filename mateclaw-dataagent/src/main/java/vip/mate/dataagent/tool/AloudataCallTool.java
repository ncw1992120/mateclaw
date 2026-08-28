package vip.mate.dataagent.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import vip.mate.tool.builtin.ToolExecutionContext;
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
import vip.mate.dataagent.model.QueryStateEntity;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.repository.DatasourceMapper;
import vip.mate.dataagent.service.*;
import vip.mate.dataagent.service.grounding.MetricQueryEvidence;
import vip.mate.dataagent.support.DataAgentChatScopeContext;
import vip.mate.dataagent.support.DataAgentChatScopeContext.ScopeResolveResult;
import vip.mate.dataagent.support.NameMatchSupport;
import vip.mate.sdk.service.MateClawRuntime;
import vip.mate.skill.knowledge.SkillScopedToolCallback;

import java.time.LocalDate;
import java.time.format.TextStyle;
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
    private final DatasourceManageService datasourceManageService;
    private final AloudataMetricDimensionMapper metricDimensionMapper;
    private final AloudataMetricMapper metricMapper;
    private final MateClawRuntime mateClawRuntime;
    private final DataAgentChatScopeContext scopeContext;
    private final SemanticRerankService semanticRerankService;
    private final QueryStateService queryStateService;

    /** 指标查询端点名，需要 ECharts 图表生成等增值逻辑 */
    private static final String METRICS_QUERY_ENDPOINT = "metrics_query";

    /** 术语检索工具名 */
    private static final String SEARCH_BUSINESS_TERM_TOOL_NAME = "search_business_term";

    /** Tool 名称前缀 */
    private static final String TOOL_PREFIX = "aloudata_";

    /** 语义搜索 Tool 名称 */
    private static final String SEARCH_SEMANTIC_TOOL_NAME = "aloudata_search_semantic";

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

    /** 消歧阈值：前两名分数差距小于此值时触发消歧追问 */
    private static final double DISAMBIGUATION_SCORE_GAP = 0.15;

    /** 指标列表展示中可用维度的最大截断数：保留足够维度供构造查询，同时防止结果超长触发 spill 截断 */
    private static final int PROMPT_MAX_AVAILABLE_DIMENSIONS = 15;

    /** 匹配指标展示名尾部的口径后缀（全角/半角括号），用于剥离得到基名 */
    private static final Pattern TRAILING_CALIBER_PATTERN =
            Pattern.compile("[（(][^（）()]*[）)]\\s*$");

    /** 提取指标展示名尾部口径后缀的括号内内容 */
    private static final Pattern TRAILING_CALIBER_CONTENT_PATTERN =
            Pattern.compile("[（(]([^（）()]*)[）)]\\s*$");

    /** 族级兜底：同基名指标族的最大捞取数量，防止极端前缀导致捞取过多（配合 ORDER BY 保证截断确定） */
    private static final int FAMILY_LOOKUP_MAX = 200;

    /** 通用口径重排：当用户原话与展示名的字符重叠度超过此阈值时，视为强相关并提升排序 */
    private static final double GENERIC_RERANK_THRESHOLD = 0.5;

    /**
     * 启动时动态注册所有 Aloudata API 端点为独立 Tool
     */
    @PostConstruct
    public void registerDynamicTools() {
        Map<String, ApiEndpoint> endpoints = endpointService.getEndpoints();
        if (MapUtils.isEmpty(endpoints)) {
            log.error("Aloudata API 端点配置为空，跳过动态 Tool 注册");
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
                      "description": "搜索关键词，用于搜索指标/维度的业务名称、同义词等。重要：请**原样保留**用户原话中的标点符号（全角/半角括号、-、_、空格等），禁止删除、改写、转换全角半角或截断关键词；系统对标点差异不敏感（如「交易市占率（整体）」与「交易市占率(整体)」能匹配同一指标），不要为对齐标点而改写关键词。保留用户原话中的所有限定词和括号内容（如「整体」「个人」「汇总」等），不要改写或精简同义词。例如用户说「交易市占率（整体）」，keyword 应传「交易市占率（整体）」而非「交易市占率」。不确定哪部分是完整指标名时，直接把用户原话整句作为 keyword 传入（宁可多检索也不截断/精简）——截断到同族短名可能被精确匹配锁死成错误指标。"
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
                + "关键词与术语名或同义词完全一致（忽略标点/空白/全角半角差异）时，"
                + "返回结果头部会出现「✅ 术语精确命中（确定性）」，请优先采信其中的标准术语名。"
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

        // 指标查询端点：注入最高优先级查询规则，让 LLM 在工具调用时刻始终可见
        if (METRICS_QUERY_ENDPOINT.equalsIgnoreCase(endpointName)) {
            sb.append("。硬性规则：1) 指标名称与用户问题完全匹配（=metricName或展示名）时直接查询，"
                    + "不要向用户做消歧确认；2) 用户未指定维度时，dimensions 默认使用指标日期（metric_time，"
                    + "常规用 metric_time__month，趋势/近N天用 metric_time__day），不要追问维度、不要凭空选维度；"
                    + "3) 指标匹配对标点不敏感（全角/半角括号、-、_、空白等差异均不影响匹配），按用户原话直接检索，"
                    + "不要因标点差异改写、删除或截断关键词。");
        }

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
                    String itemType = extractArrayItemType(param.getType());
                    // 兼容配置中仅写 "Array" 未声明元素类型的对象数组参数（如 orders）：
                    // 若不识别为对象数组，schema 会把 items 声明为 string，误导 LLM 传字符串数组
                    // （如 ["{\"metric_time__day\": \"asc\"}"]），进而触发参数类型异常
                    if ("string".equals(itemType) && isObjectArrayParam(param)) {
                        itemType = "object";
                    }
                    JSONObject items = new JSONObject(new LinkedHashMap<>());
                    items.set("type", itemType);
                    prop.set("items", items);
                    if ("object".equals(itemType)) {
                        String desc = prop.getStr("description", "");
                        desc += "。注意：数组每个元素必须是对象（键值对），禁止传字符串数组或整体 JSON 字符串，示例 [{\"metric_time__day\": \"asc\"}]";
                        prop.set("description", desc);
                    }
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
        } else if (apiType.contains("[Object]") || apiType.contains("[Map]")) {
            return "object";
        } else if (apiType.contains("[Integer]")) {
            return "integer";
        }
        return "string";
    }

    /**
     * 判断数组参数的元素是否为对象（Map）。
     * <p>
     * 端点配置里 orders 等参数类型仅写 "Array"、未声明元素类型，但真实 API 契约是对象数组。
     * 这里按参数名/类型声明兜底识别，避免生成的 JSON Schema 把 items 写成 string 误导 LLM
     * 传字符串数组（历史线上出现过 "orders 参数构造异常" 的根因）。
     */
    private boolean isObjectArrayParam(ApiParam param) {
        String type = param.getType() != null ? param.getType() : "";
        String name = param.getName() != null ? param.getName() : "";
        return type.contains("Map") || type.contains("Object") || "orders".equalsIgnoreCase(name);
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
            log.error("参数 [{}] 默认值 '{}' 无法按类型 {} 解析，保留字符串", param.getName(), defaultVal, type);
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
            // conversationId 从 ToolExecutionContext 读取：该 ThreadLocal 由 ToolExecutionExecutor
            // 在工具执行线程本身上 set，内联与并行批（虚拟线程）路径均可靠。
            // 不能用 ChatOriginHolder——它在并行批次的虚拟线程上是 EMPTY，会导致白名单解析落空。
            String dsConvId = ToolExecutionContext.conversationId();
            ScopeResolveResult<Long> dsScope = scopeContext.resolveDatasourceId(dsConvId, datasourceId);
            if (dsScope.hasError()) {
                return error(dsScope.getErrorMessage());
            }
            datasourceId = dsScope.getResolvedValue();

            // datasourceId 自动注入强化：白名单为空时尝试自动查找
            if (datasourceId == null && !scopeContext.hasScope(dsConvId)) {
                datasourceId = autoResolveDatasourceId();
            }
            if (datasourceId == null) {
                return error("需要 datasourceId 参数指定数据源");
            }

            // 校验数据源
            String validationError = validateAloudataDatasource(datasourceId);
            if (validationError != null) {
                // 无白名单时，校验失败（不存在/已禁用/无权限）附带可用数据源列表引导，
                // 防止 LLM 拿到"数据源不存在"后继续凭空猜测幻 ID 浪费轮次（与语义检索路径的
                // buildDatasourceGuide 引导行为保持一致）
                if (!scopeContext.hasScope(dsConvId) && isDatasourceIdentityError(validationError)) {
                    return error(validationError + "\n" + buildDatasourceGuide());
                }
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
            if (METRICS_QUERY_ENDPOINT.equalsIgnoreCase(endpointName)) {
                // P0: 强制 queryResultType 为 DATA，避免 SQL_AND_DATA 导致数据截断
                params.put("queryResultType", "DATA");

                // P0: timeConstraint 自动规范化
                normalizeTimeConstraint(params);

                // P0: orders 参数形态归一化——兼容字符串数组/整体JSON字符串等 LLM 常见错误写法，
                // 统一转为对象数组；无法解析时返回可读错误，而不是抛 ClassCastException
                String ordersError = normalizeOrders(params);
                if (ordersError != null) {
                    return error(ordersError);
                }

                // P0+P1: 查询请求校验（中文展示名、同环比约束、占比/排名维度、维度可用性）
                List<String> validationErrors = validateMetricsQueryRequest(datasourceId, params);
                if (!validationErrors.isEmpty()) {
                    return error("查询请求校验失败：\n" + String.join("\n", validationErrors)
                            + "\n请修正后重试。");
                }
            }

            // 调用 API（认证 Header 由 apiClient 自动注入）
            ResponseEntity<Map> response = apiClient.callWithParams(endpointName, config, params);

            // P0-1/P0-2: 指标查询【成功】后记录证据（RAW 结果，供最终答案数字对齐校验）并持久化查询基座
            if (METRICS_QUERY_ENDPOINT.equalsIgnoreCase(endpointName)) {
                recordMetricQueryEvidence(datasourceId, params, response);
            }

            // 格式化响应
            return formatResponse(endpointName, response);
        } catch (IllegalArgumentException e) {
            log.error("Aloudata Tool [{}] 参数校验失败: {}", endpointName, e.getMessage());
            return error(e.getMessage());
        } catch (ClassCastException e) {
            // 防御：任何参数类型强转失败都给出可读指引，避免 LLM 拿到晦涩的 JVM 异常信息
            log.error("Aloudata Tool [{}] 参数类型错误: {}", endpointName, e.getMessage());
            return error("参数类型错误: " + e.getMessage()
                    + "。请检查参数结构是否符合 API 契约：orders 必须为对象数组（如 [{\"metric_time__day\": \"asc\"}]），"
                    + "metrics/dimensions/filters 必须为字符串数组。");
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
            // 远程 API 错误字段不统一，尝试从多个常见字段提取；都为空时转储完整响应体兜底，
            // 避免 LLM 拿到 "返回错误: null" 后只能盲目变换参数重试
            String combinedMsg = extractApiErrorMessage(responseBody);
            if (combinedMsg == null) {
                log.error("Aloudata API [{}] 返回失败且无错误明细，完整响应: {}", endpointName, JSONUtil.toJsonStr(responseBody));
                String bodyPreview = JSONUtil.toJsonStr(responseBody);
                if (bodyPreview.length() > 600) {
                    bodyPreview = bodyPreview.substring(0, 600) + "...(截断)";
                }
                return error("API: " + endpointName + " 返回失败（success=false），但未携带错误信息。完整响应: " + bodyPreview
                        + "\n提示: 此类错误多为数据源查询通道/查询引擎问题而非参数格式问题，请检查数据源（指标应用→API集成）的查询服务地址与认证配置；若连续 5 次返回相同错误，请停止重试并向用户说明。");
            }

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
            // 记录 LLM 原始传值：确定性解析可能将其重定向到唯一可用源，需在结果中显式告知
            Long requestedId = datasourceId;

            // 解析数据源：确定性优先（白名单 > 唯一 Aloudata 源 > 校验 LLM 传值 > 列表引导），
            // 不盲信 LLM 猜测的 datasourceId（避免无白名单时用一个幻觉出的 id=1 静默跑错源）。
            // conversationId 从 ToolExecutionContext 读取：该 ThreadLocal 由 ToolExecutionExecutor
            // 在工具执行线程本身上 set，内联与并行批（虚拟线程）路径均可靠。
            // 不能用 ChatOriginHolder——它在并行批次的虚拟线程上是 EMPTY，会导致白名单解析落空。
            String dsConvId = ToolExecutionContext.conversationId();
            ScopeResolveResult<Long> dsScope = resolveAloudataDatasourceId(dsConvId, datasourceId);
            if (dsScope.hasError()) {
                return error(dsScope.getErrorMessage());
            }
            datasourceId = dsScope.getResolvedValue();
            if (datasourceId == null) {
                return error(buildDatasourceGuide());
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
                String result = handleAloudataSemanticSearch(datasourceId, keyword, topK, threshold);
                return prependDatasourceRedirectNote(requestedId, datasourceId, result);
            } else {
                // 非 Aloudata 数据源：保持原有表级检索
                String result = handleGenericSemanticSearch(datasourceId, keyword, topK, threshold);
                return prependDatasourceRedirectNote(requestedId, datasourceId, result);
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

            /* 精确命中前置（确定性）：关键词与术语名/同义词归一化后完全相等时，该术语即为用户所指，
             * 展示在结果最前并标注，LLM 优先采信其标准术语名；不受 ES 打分/topK 截断影响。 */
            List<BusinessTermSearchResult.TermHit> exactHits = businessTermEsService.exactSearch(keyword, null);
            if (exactHits != null && !exactHits.isEmpty()) {
                sb.append("## ✅ 术语精确命中（确定性）\n\n");
                sb.append("关键词「").append(keyword)
                        .append("」与以下术语的名称/同义词**完全一致**（忽略标点/空白/全角半角差异），请优先采信其标准术语名：\n");
                for (BusinessTermSearchResult.TermHit hit : exactHits) {
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
                    if (hit.getRelatedMetricNames() != null && !hit.getRelatedMetricNames().isEmpty()) {
                        sb.append(" [关联指标: ").append(String.join(", ", hit.getRelatedMetricNames())).append("]");
                    }
                    if (hit.getRelatedDimensionNames() != null && !hit.getRelatedDimensionNames().isEmpty()) {
                        sb.append(" [关联维度: ").append(String.join(", ", hit.getRelatedDimensionNames())).append("]");
                    }
                    sb.append(" [来源: 精确命中]\n");
                }
                sb.append("\n");
            }

            int hitCount = searchResult.getTermHits() != null ? searchResult.getTermHits().size() : 0;
            sb.append("**匹配结果**: ").append(hitCount).append(" 个术语");
            if (exactHits != null && !exactHits.isEmpty()) {
                sb.append("（另精确命中 ").append(exactHits.size()).append(" 个，见上方 ✅ 块）");
            }
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
                    if (hit.getRelatedMetricNames() != null && !hit.getRelatedMetricNames().isEmpty()) {
                        sb.append(" [关联指标: ").append(String.join(", ", hit.getRelatedMetricNames())).append("]");
                    }
                    if (hit.getRelatedDimensionNames() != null && !hit.getRelatedDimensionNames().isEmpty()) {
                        sb.append(" [关联维度: ").append(String.join(", ", hit.getRelatedDimensionNames())).append("]");
                    }
                    sb.append(" [分数: ").append(String.format("%.3f", hit.getScore()));
                    sb.append(", 来源: ").append(hit.getMatchSource()).append("]\n");
                }
                sb.append("\n");
            }

            // 混合检索无命中且无精确命中时才提示"未找到"；精确命中已在上方展示，避免自相矛盾的提示
            if (hitCount == 0 && (exactHits == null || exactHits.isEmpty())) {
                sb.append("未找到匹配的业务术语。请尝试更换关键词。");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("业务术语搜索失败: {}", e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    /** 指标名称精确匹配结果：用户问题与某个指标名称完全匹配时 resolved()==true */
    private record ExactMetricMatch(boolean resolved, AloudataMetricEntity metric) {
        static ExactMetricMatch none() {
            return new ExactMetricMatch(false, null);
        }
    }

    /**
     * P0: 指标名称精确匹配解析。
     * <p>
     * 当用户问题/关键词与某指标的 metricName 或 metricDisplayName **完全相等**（去首尾空白，
     * 英文不区分大小写）时，说明用户已明确指明指标，应当**直接查询**，不再触发任何消歧判断。
     * 多个指标同名（如不同数据集都叫「订单量」）时仍返回未解析，交由原有消歧流程。
     * 该解析走元数据表精确查询，不受 ES 打分 / TopK 截断影响。
     */
    private ExactMetricMatch resolveExactMetricMatch(Long datasourceId, String keyword, String originalMessage) {
        if (datasourceId == null) {
            return ExactMetricMatch.none();
        }
        String[] probes = {keyword, originalMessage};
        // keyword 探针可信度守卫：keyword 相对原话被严重截断时跳过 keyword 探针（见 isKeywordProbeTruncated），
        // 防止「截断关键词恰好等于同族短名指标」被精确锁定成错误目标；该场景交给族级兜底用原话整名命中
        boolean skipKeywordProbe = isKeywordProbeTruncated(keyword, originalMessage);
        for (int i = 0; i < probes.length; i++) {
            String probe = probes[i];
            if (probe == null) {
                continue;
            }
            if (i == 0 && skipKeywordProbe) {
                continue;
            }
            String p = probe.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                List<AloudataMetricEntity> byName = metricMapper.selectList(
                        new LambdaQueryWrapper<AloudataMetricEntity>()
                                .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                                .eq(AloudataMetricEntity::getMetricName, p)
                                .select(AloudataMetricEntity::getMetricName,
                                        AloudataMetricEntity::getMetricDisplayName,
                                        AloudataMetricEntity::getType,
                                        AloudataMetricEntity::getBusinessCaliber,
                                        AloudataMetricEntity::getMetricCategoryName,
                                        AloudataMetricEntity::getUnit,
                                        AloudataMetricEntity::getSynonyms)
                                .last("LIMIT 10"));
                List<AloudataMetricEntity> byDisplay = metricMapper.selectList(
                        new LambdaQueryWrapper<AloudataMetricEntity>()
                                .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                                .eq(AloudataMetricEntity::getMetricDisplayName, p)
                                .select(AloudataMetricEntity::getMetricName,
                                        AloudataMetricEntity::getMetricDisplayName,
                                        AloudataMetricEntity::getType,
                                        AloudataMetricEntity::getBusinessCaliber,
                                        AloudataMetricEntity::getMetricCategoryName,
                                        AloudataMetricEntity::getUnit,
                                        AloudataMetricEntity::getSynonyms)
                                .last("LIMIT 10"));
                List<AloudataMetricEntity> merged = new ArrayList<>(byName);
                for (AloudataMetricEntity m : byDisplay) {
                    if (m.getMetricName() != null && merged.stream()
                            .noneMatch(x -> m.getMetricName().equals(x.getMetricName()))) {
                        merged.add(m);
                    }
                }
                if (merged.size() == 1) {
                    return new ExactMetricMatch(true, merged.get(0));
                }
                if (merged.size() > 1) {
                    // 多个指标同名仍有多义性，交由消歧流程
                    return ExactMetricMatch.none();
                }
            } catch (Exception e) {
                log.error("指标名称精确匹配查询失败，跳过（不影响原有检索）: {}", e.getMessage());
            }
        }

        /* 标点/空白不敏感兜底：SQL 精确相等失败时（如 全角括号 vs 半角括号、连接符/下划线/空白差异，
         * LLM 对用户带标点原话做了截断或格式化），归一化后与 metricName/展示名比对。 */
        for (int i = 0; i < probes.length; i++) {
            String probe = probes[i];
            if (probe == null) {
                continue;
            }
            if (i == 0 && skipKeywordProbe) {
                continue;
            }
            String p = probe.trim();
            if (p.isEmpty()) {
                continue;
            }
            List<AloudataMetricEntity> normalizedMatches = resolveNormalizedMetricMatch(datasourceId, p);
            if (!normalizedMatches.isEmpty()) {
                if (normalizedMatches.size() == 1) {
                    return new ExactMetricMatch(true, normalizedMatches.get(0));
                }
                // 多个归一化同名词仍有多义性，交由消歧流程
                return ExactMetricMatch.none();
            }
        }
        return ExactMetricMatch.none();
    }

    /**
     * 标点/空白不敏感的指标名称匹配（兜底）。
     * <p>
     * 用户问题/关键词与指标名在去除标点、空白、全角半角差异（括号、-、_ 等）后**完全相等**时判定为命中。
     * 覆盖 LLM 对用户带标点原话的截断 / 格式化导致的微小位移（如 展示名「销售金额（含税）」 命中
     * 关键词「销售金额(含税)」或「销售金额含税」）。
     * <p>
     * 候选集：先按「剥离尾部口径后的基名」前缀查询缩小范围；前缀未命中时（名称分量被全角括号等隔断，
     * 如 base=「销售金额含税」无法前缀命中 display=「销售金额（含税）」），退化为按 metricName/展示名
     * 两列的有界全量采样，在内存中做归一化比对。仅用两列查询（列裁剪），开销可忽略。
     */
    private List<AloudataMetricEntity> resolveNormalizedMetricMatch(Long datasourceId, String probe) {
        String normalized = normalizeKey(probe);
        if (normalized.isEmpty()) {
            return List.of();
        }
        try {
            String base = stripTrailingCaliber(probe);
            List<AloudataMetricEntity> candidates;
            if (!base.isEmpty()) {
                candidates = metricMapper.selectList(
                        new LambdaQueryWrapper<AloudataMetricEntity>()
                                .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                                .likeRight(AloudataMetricEntity::getMetricDisplayName, base)
                                .select(AloudataMetricEntity::getMetricName,
                                        AloudataMetricEntity::getMetricDisplayName)
                                .orderByAsc(AloudataMetricEntity::getMetricDisplayName)
                                .last("LIMIT 500"));
            } else {
                candidates = List.of();
            }
            if (candidates.isEmpty()) {
                candidates = metricMapper.selectList(
                        new LambdaQueryWrapper<AloudataMetricEntity>()
                                .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                                .select(AloudataMetricEntity::getMetricName,
                                        AloudataMetricEntity::getMetricDisplayName)
                                .orderByAsc(AloudataMetricEntity::getMetricName)
                                .last("LIMIT 1500"));
            }
            return candidates.stream()
                    .filter(m -> normalized.equals(normalizeKey(m.getMetricName()))
                            || (m.getMetricDisplayName() != null
                            && normalized.equals(normalizeKey(m.getMetricDisplayName()))))
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.error("标点不敏感指标名称匹配查询失败，跳过（不影响原有检索）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * keyword 探针可信度守卫（防截断劫持）。
     * <p>
     * 背景：{@link #resolveExactMetricMatch} 的 probes = {keyword, originalMessage} 依次精确匹配，
     * 先命中的探针直接锁定目标并输出「禁止消歧」。当 LLM 把 keyword 截断成同族基名/短名（如
     * 原话「查询昨天的期货收入-应收-当年」→ keyword「期货收入」），且库里恰好存在一个名为「期货收入」
     * 的短名指标时，keyword 探针会把这个**短名指标**锁成目标，而用户真正要的是「期货收入-应收-当年」——
     * 精确匹配把错误目标锁死且禁止消歧，属于错查而非多问。
     * <p>
     * 守卫：keyword 含中文、且其归一化长度不足原话归一化长度的 90% 时，判定 keyword 被严重截断，
     * 跳过 keyword 探针（SQL 精确相等与归一化相等两轮都跳过），改由族级兜底用「原话整名命中」锁定
     * 正确成员；族级也定不出时落到消歧（安全保守，宁多问不错查）。
     * <p>
     * 阈值说明：早期为 80%，但用户原话本身即为指标名短语（无废话修饰词）时，截断掉 2-3 个限定字
     * （如「大客户月度销售金额（含税）」→「大客户月度销售金额」）恰好落在 80% 窗口内逃过守卫，
     * 短名探针仍会把同族短名指标锁死。收紧到 90% 后该窗口被覆盖；原话带修饰词的长问句场景
     * （keyword 相对整句原话通常远低于 90%）行为不受影响。
     * <p>
     * 仅对中文 keyword 生效：用户完整写出英文 metricName（如 sales_amount）时不受影响，
     * 即使原话带更长的中文上下文也照常走 SQL 精确相等。
     */
    private boolean isKeywordProbeTruncated(String keyword, String originalMessage) {
        if (keyword == null || originalMessage == null || originalMessage.isBlank()) {
            return false;
        }
        String nKw = normalizeKey(keyword);
        String nOrig = normalizeKey(originalMessage);
        if (nKw.isEmpty() || nOrig.isEmpty() || nKw.equals(nOrig)) {
            return false;
        }
        if (!nKw.matches(".*[\\u4e00-\\u9fff].*")) {
            // 纯英文 metricName 不受守卫影响
            return false;
        }
        return nKw.length() < (int) Math.ceil(nOrig.length() * 0.9);
    }

    /**
     * 追加"指标名称精确匹配"提示：明确告知 LLM 目标指标已由名称完全匹配唯一确定，
     * 直接使用该 metricName 构造查询，禁止再向用户做消歧确认。
     * <p>
     * 若匹配展示名未完整覆盖用户原话中的限定内容（如 keyword 被截断为同族短名，
     * 原话「大客户月度销售金额（含税）」→ keyword「大客户月度销售金额」），追加限定词
     * 覆盖核对警告，提示 LLM 不要盲目采信该精确匹配（与 isKeywordProbeTruncated
     * 的探针守卫互为兜底：守卫管代码侧锁定，本警告管 LLM 侧采信）。
     */
    private void appendExactMatchHint(StringBuilder sb, AloudataMetricEntity metric, String keyword,
                                      String originalMessage) {
        sb.append("## ✅ 指标名称精确匹配\n\n");
        sb.append("用户问题/关键词「").append(keyword).append("」与指标名称**完全匹配**，目标指标已唯一确定：\n");
        sb.append("  - **").append(metric.getMetricName()).append("**(")
                .append(metric.getMetricDisplayName() != null ? metric.getMetricDisplayName() : "").append(")");
        if (metric.getBusinessCaliber() != null && !metric.getBusinessCaliber().isBlank()) {
            sb.append(" — ").append(metric.getBusinessCaliber());
        }
        sb.append("\n\n");

        /* 限定词覆盖核对：匹配展示名（归一化）是原话（归一化）的真子集且原话还有额外限定字符时，
         * 说明存在 keyword 截断/精简导致的短名劫持风险，警告 LLM 复核后再采信。
         * 命中时最终指令改为「先复核再使用」，避免与下方「直接使用」指令自相矛盾。 */
        boolean qualifierWarning = false;
        if (originalMessage != null && !originalMessage.isBlank() && !originalMessage.equals(keyword)) {
            String display = metric.getMetricDisplayName() != null ? metric.getMetricDisplayName() : "";
            String normOrig = normalizeKey(originalMessage);
            String normName = normalizeKey(!display.isBlank() ? display : metric.getMetricName());
            if (normOrig.length() > normName.length() && normOrig.contains(normName)) {
                String extra = extractExtraQualifierChars(originalMessage, display);
                if (!extra.isEmpty()) {
                    qualifierWarning = true;
                    sb.append("> ⚠️ 限定词覆盖核对：用户原话「").append(originalMessage)
                            .append("」中还包含限定内容「").append(extra).append("」，未被匹配展示名「")
                            .append(display).append("」覆盖。若这些限定内容确实属于该指标口径")
                            .append("（如（含税）（整体）（汇总）等），**禁止直接采信本精确匹配**，")
                            .append("应改用完整原话重新调用 aloudata_search_semantic 检索，")
                            .append("或向用户确认目标指标口径后再查询。\n\n");
                }
            }
        }

        if (qualifierWarning) {
            sb.append("> **本精确匹配存在限定词覆盖疑问，请先按上方 ⚠️ 警告复核**：改用完整原话重新调用")
                    .append(" aloudata_search_semantic，或向用户确认目标指标口径；")
                    .append("确认无误后再使用 metricName=").append(metric.getMetricName()).append(" 构造查询。\n\n");
        } else {
            sb.append("> **直接使用 metricName=").append(metric.getMetricName())
                    .append(" 构造查询，不要再向用户进行消歧确认**。该结果来自指标元数据的名称精确匹配，是最可靠的目标；"
                            + "若需要维度且用户未指定，请用默认指标时间维度（metric_time，如 metric_time__month）。\n\n");
        }
    }

    /**
     * 提取原话中未被指标展示名覆盖的限定中文字符，用于精确匹配的截断校验提示。
     * <p>
     * 仅当展示名是原话字面子串时生效（原话含全角/半角差异时不提取，避免误报）；
     * 残余内容（如（含税）（整体）及"帮我查下"等修饰词）一并返回，由 LLM 自行判断
     * 哪些属于真正缺失的指标口径。超长时截断展示。
     */
    private static String extractExtraQualifierChars(String original, String display) {
        if (original == null || display == null || display.isBlank() || !original.contains(display)) {
            return "";
        }
        String residue = original.replace(display, "").replaceAll("[^\\u4e00-\\u9fff（）()]", "");
        if (residue.isEmpty()) {
            return "";
        }
        return residue.length() > 20 ? residue.substring(0, 20) + "…" : residue;
    }

    /**
     * 构建"## 指标"列表展示行（压缩模式）。
     * <p>
     * 相比 MetricHit.getPromptInfo()：业务口径 + 可用维度超过 PROMPT_MAX_AVAILABLE_DIMENSIONS 时截断并标注总数。
     * 防止单条指标因几十个维度把整体检索结果顶到 spill 阈值，导致 LLM 只能看到 preview 而遗漏后排候选
     * （"用户问题完全匹配指标名称"场景无法枚举全部候选、消歧场景只看到前几个候选的根因之一）。
     */
    private String compactMetricPrompt(AloudataSearchResult.MetricHit hit) {
        StringBuilder sb = new StringBuilder();
        sb.append(hit.getMetricName());
        if (hit.getMetricDisplayName() != null && !hit.getMetricDisplayName().isBlank()) {
            sb.append("(").append(hit.getMetricDisplayName()).append(")");
        }
        if (hit.getType() != null && !hit.getType().isBlank()) {
            sb.append(" [").append(hit.getType()).append("]");
        }
        if (hit.getBusinessCaliber() != null && !hit.getBusinessCaliber().isBlank()) {
            sb.append(" - ").append(hit.getBusinessCaliber());
        }
        List<String> dims = hit.getAvailableDimensions();
        if (dims != null && !dims.isEmpty()) {
            if (dims.size() <= PROMPT_MAX_AVAILABLE_DIMENSIONS) {
                sb.append(", 可用维度: ").append(String.join(", ", dims));
            } else {
                sb.append(", 可用维度(前").append(PROMPT_MAX_AVAILABLE_DIMENSIONS).append("个): ")
                        .append(String.join(", ", dims.subList(0, PROMPT_MAX_AVAILABLE_DIMENSIONS)))
                        .append(" …共").append(dims.size())
                        .append("个；如需完整可用维度请调用 aloudata_metric_available_dimensions");
            }
        }
        return sb.toString();
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
        if (!"completed".equalsIgnoreCase(syncStatus.status())) {
            /* 新版语义层未同步，降级到旧版表级检索 */
            log.info("Aloudata 数据源 [{}] 新版语义层未同步，降级到旧版表级检索", datasourceId);
            return handleGenericSemanticSearch(datasourceId, keyword, topK, threshold);
        }

        /* P1: 自动先查业务术语，扩展检索关键词 */
        List<String> expandedKeywords = expandKeywordsFromBusinessTerms(keyword);

        /* 补充用户原话中未被 LLM keyword 覆盖的差集片段，防止 LLM 精简丢失关键限定信息（如括号内的"整体""汇总"等）。
         * 仅在 keyword 是原话子串时注入差集，避免多指标提问时跨检索串味（如"对比整体和个人"不应同时给两者加分），
         * 也避免整句废话词污染 should 打分。 */
        String originalMessage = getOriginalMessage();
        if (originalMessage != null && !originalMessage.equals(keyword)) {
            String complement = extractKeywordComplement(originalMessage, keyword);
            if (complement != null && !complement.isEmpty() && !expandedKeywords.contains(complement)) {
                expandedKeywords.add(complement);
            }
        }

        /* 标点还原：LLM 常对用户带标点的原话做截断/格式化（如 用户「销售金额（含税）」→ keyword「销售金额」
         * 或「销售金额含税」），导致 ES 关键词侧匹配偏差。当 keyword 的归一化形态是原话归一化形态的子集、
         * 且覆盖 ≥60% 长度时，认为 keyword 是原话的"去标点/精简"退化，把带标点的原话整体追加为检索词
         * （hybridSearchEnhanced 中扩展词是 OR should 语义，命中即加分；不命中不影响召回），
         * 与下方 originalMessage 并行的向量路径互补，抵消 LLM 格式化造成的失配。 */
        String normOrig = normalizeKey(originalMessage);
        String normKw = normalizeKey(keyword);
        if (!normOrig.isEmpty() && !normOrig.equals(normKw)
                && normKw.length() >= (int) Math.ceil(normOrig.length() * 0.6)
                && normOrig.contains(normKw)
                && !expandedKeywords.contains(originalMessage)) {
            log.info("关键词标点还原：keyword='{}' 由原话 '{}' 归一化退化，追加带标点原话为检索词", keyword, originalMessage);
            expandedKeywords.add(originalMessage);
        }

        /* 增强混合检索：传入用户原话作为并行向量检索路径，降低对 LLM keyword 压缩质量的敏感度 */
        AloudataSearchResult sr = aloudataSemanticEsService.hybridSearchEnhanced(
                datasourceId, expandedKeywords, originalMessage, topK, threshold);
        List<AloudataSearchResult.MetricHit> mergedMetrics = sr.getMetricHits() != null ? new ArrayList<>(sr.getMetricHits()) : new ArrayList<>();
        List<AloudataSearchResult.DimensionHit> mergedDimensions = sr.getDimensionHits() != null ? new ArrayList<>(sr.getDimensionHits()) : new ArrayList<>();

        /* 按分数重排 */
        mergedMetrics.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        mergedDimensions.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        /* 限制总数（转为独立可变列表，便于后续族级兜底注入缺失成员） */
        if (mergedMetrics.size() > topK) {
            mergedMetrics = new ArrayList<>(mergedMetrics.subList(0, topK));
        }
        if (mergedDimensions.size() > topK) {
            mergedDimensions = new ArrayList<>(mergedDimensions.subList(0, topK));
        }

        /* P0: 指标名称精确匹配 —— 用户问题完全匹配指标名称时直接锁定目标，跳过消歧判断 */
        ExactMetricMatch exactMatch = resolveExactMetricMatch(datasourceId, keyword, originalMessage);

        FamilyBackfillResult family;
        boolean familyResolved;
        if (exactMatch.resolved()) {
            // 精确命中：将该指标置为最高分并确保出现在结果首位，不触发族级/分数消歧
            double dominant = mergedMetrics.stream()
                    .mapToDouble(AloudataSearchResult.MetricHit::getScore)
                    .max().orElse(1.0) + 1.0;
            AloudataMetricEntity target = exactMatch.metric();
            boolean absent = true;
            if (target.getMetricName() != null) {
                for (AloudataSearchResult.MetricHit hit : mergedMetrics) {
                    if (target.getMetricName().equals(hit.getMetricName())) {
                        hit.setScore(dominant);
                        absent = false;
                        break;
                    }
                }
            }
            if (absent && target.getMetricName() != null) {
                mergedMetrics.add(toMetricHit(target, dominant));
            }
            mergedMetrics.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            enrichBackfilledDimensions(datasourceId, mergedMetrics);
            family = new FamilyBackfillResult(null, List.of(), List.of());
            familyResolved = true;
        } else {
            /* 确定性族级兜底 + 宽泛查询族级聚合：仅当检索结果打满 topK 时触发。
             * 打满说明候选池/截断边界处仍有大量候选（用户输入宽泛词时尤其常见），
             * 目标指标可能因 topK 截断被排除；按基名确定性捞全整族、补入缺失成员，
             * 保证目标指标族必然出现在结果中（详见 backfillMetricFamily 注释）。
             * 结果未打满（<topK）时不存在截断风险，跳过兜底。 */
            family = (mergedMetrics.size() >= topK)
                    ? backfillMetricFamily(datasourceId, keyword, originalMessage, mergedMetrics)
                    : new FamilyBackfillResult(null, List.of(), List.of());
            familyResolved = family.triggered() && !family.matched().isEmpty();
        }

        /* 通用口径重排：用"用户原话 vs 展示名"的字符重叠度做通用重排，将用户原话中信息量覆盖最充分的指标提权到首位。
         * 仅当族级兜底已解析出确定性目标（matched 非空，dominant 分已置位）时跳过——避免覆盖权威排序；
         * 族级已触发但 matched 为空（如宽泛词聚合多成员无括号口径，无法唯一确定）时仍执行重排，
         * 借助原话重叠度把最匹配的指标（如原话"我想看保费收入"、keyword="保费"时）提权首位，减少 LLM 额外确认。 */
        if (!familyResolved) {
            applyGenericCaliberRerank(originalMessage, keyword, mergedMetrics);
        }

        /* Rerank 精排分支：由系统配置 dataagent.search.rerank.enabled 控制（默认关闭）。
         * 开启且配置了默认 rerank 模型时，对 TopK 截断后的指标/维度候选按与用户原话的
         * 相关度二次精排，提升命中项排序准确度；开关关闭或调用失败时静默降级为原始排序。
         * 指标名称精确匹配时跳过重排，避免打乱权威排序。 */
        if (!exactMatch.resolved()) {
            SemanticRerankOutput rerankOutput = semanticRerankService.rerankSemanticHits(
                    mergedMetrics, mergedDimensions, originalMessage != null ? originalMessage : keyword);
            if (rerankOutput.isReranked()) {
                mergedMetrics = rerankOutput.getMetricHits();
                mergedDimensions = rerankOutput.getDimensionHits();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**搜索关键词**: ").append(keyword);
        if (expandedKeywords.size() > 1) {
            sb.append("（已扩展: ").append(String.join(", ", expandedKeywords.subList(1, expandedKeywords.size()))).append(")");
        }
        sb.append("\n");
        sb.append("**数据源 ID**: ").append(datasourceId).append("\n");
        // 时间锚点注入：相对时间（上周五/上月同期等）换算必须以此为准，禁止 LLM 主观推算当前日期与星期
        LocalDate today = LocalDate.now();
        String week = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        sb.append("**当前日期**: ").append(today).append("（").append(week).append("）\n");

        int metricCount = mergedMetrics.size();
        sb.append("**匹配结果**: ").append(metricCount).append(" 个指标\n\n");

        /* 确定性匹配提示：精确命中时直接告知目标并禁止消歧；否则走族级口径提示 + 消歧判断。
         * 前置到指标/维度列表之前，确保即使结果超长触发 spill，
         * LLM 也能在 preview 中看到消歧和族级口径信息（这些是检索结果中最重要的决策依据）。 */
        if (exactMatch.resolved()) {
            appendExactMatchHint(sb, exactMatch.metric(), keyword, originalMessage);
        } else {
            appendFamilyHint(sb, family, keyword);
            appendDisambiguationHint(sb, mergedMetrics, mergedDimensions, keyword, family.triggered());
        }

        /* 展示指标命中 */
        if (!mergedMetrics.isEmpty()) {
            sb.append("## 指标\n\n");
            for (AloudataSearchResult.MetricHit hit : mergedMetrics) {
                sb.append("- ").append(compactMetricPrompt(hit));
                sb.append(" [分数: ").append(String.format("%.2f", hit.getScore()));
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

        /* 指标-维度配对约束：强制 LLM 按映射关系选维度，禁止跨指标错配 */
        if (!mergedMetrics.isEmpty()) {
            sb.append("## ⚠️ 指标-维度配对规则（硬约束）\n\n");
            sb.append("- 每个指标的「可用维度」是该指标**唯一合法**的维度来源，构造 dimensions 时");
            sb.append("只能从所选指标的 availableDimensions 中选取，禁止混入其他指标的维度。\n");
            sb.append("- 当查询多个指标时，dimensions 中的每个维度必须**同时**属于所有被查指标的可用维度集");
            sb.append("（取交集），否则查询会报错。\n");
            sb.append("- 向用户展示候选维度时，必须标注每个维度属于哪个指标，例如");
            sb.append("「销售额可用维度：区域/省份/城市」，禁止脱离指标单独列维度让用户选。\n");
            sb.append("- 构造查询前自检：对 dimensions 中的每个 dimName，确认它出现在所选指标的");
            sb.append("availableDimensions 列表中；若不在，移除或换用该指标的可用维度。\n");
            sb.append("- **用户未指定维度时，dimensions 默认使用指标日期（metric_time）**：月度/常规统计用");
            sb.append("metric_time__month，趋势/近N天用 metric_time__day；不要凭空选用其他维度，也不要向用户追问维度。");
            sb.append("仅当用户明确要「累计/总计/合计」汇总时才可省略 dimensions。\n\n");
        }

        /* P3: 检索失败自动降级 */
        if (metricCount == 0) {
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
        if (!isVisibleEnabledDatasource(datasourceId)) {
            return "当前用户无权限访问该数据源, id=" + datasourceId;
        }
        return null;
    }

    /**
     * 校验数据源是否为 Aloudata 类型 且 当前用户有权限（可见且已启用）
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
        if (!isVisibleEnabledDatasource(datasourceId)) {
            return "当前用户无权限访问该数据源, id=" + datasourceId;
        }
        return null;
    }

    /**
     * 判断校验错误是否属于"数据源身份类"错误（不存在/已禁用/无权限）。
     * <p>
     * 这类错误的根因是 LLM 猜错/编造了 datasourceId，附带可用列表引导即可纠正；
     * "类型不是 aloudata"等结构性错误不在此列（引导列表帮不上忙，保持原错误信息）。
     */
    private boolean isDatasourceIdentityError(String validationError) {
        return validationError != null
                && (validationError.startsWith("数据源不存在")
                || validationError.contains("已禁用")
                || validationError.contains("无权限"));
    }

    private String error(String message) {
        return JSONUtil.toJsonStr(new JSONObject().set("error", message));
    }

    // ==================== 查询请求校验与自动修正 ====================

    /**
     * P0: timeConstraint 自动规范化
     * <p>
     * 1. 确保外层有括号包裹
     * 1. 修复未用方括号的 metric_time 引用
     * 2. 替换 BETWEEN ... AND ... 为 >= AND <=（支持双引号和单引号）
     * 3. 确保外层括号
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

        // 1. 修复未用方括号的 metric_time 引用（如 metric_time__day → [metric_time__day]）
        // 仅在非方括号内的 metric_time 引用
        tc = tc.replaceAll("(?<!\\[)(metric_time__(?:day|month|year|week|quarter))(?![\\]\\w])", "[$1]");

        // 2. 替换 BETWEEN ... AND ... 为 >= AND <=
        // 匹配: [metric_time__day] BETWEEN "2024-01-01" AND "2024-01-31"（双引号）
        tc = tc.replaceAll(
                "\\[metric_time__(\\w+)\\]\\s*[Bb][Ee][Tt][Ww][Ee][Ee][Nn]\\s*\"([^\"]+)\"\\s+[Aa][Nn][Dd]\\s*\"([^\"]+)\"",
                "[metric_time__$1]>=\"$2\" AND [metric_time__$1]<=\"$3\""
        );
        // 匹配: [metric_time__day] BETWEEN '2024-01-01' AND '2024-01-31'（单引号）
        tc = tc.replaceAll(
                "\\[metric_time__(\\w+)\\]\\s*[Bb][Ee][Tt][Ww][Ee][Ee][Nn]\\s*'([^']+)'\\s+[Aa][Nn][Dd]\\s*'([^']+)'",
                "[metric_time__$1]>=\"$2\" AND [metric_time__$1]<=\"$3\""
        );

        // 3. 确保外层括号
        if (!tc.startsWith("(") || !tc.endsWith(")")) {
            tc = "(" + tc + ")";
        }

        if (!tc.equals(tcObj.toString())) {
            log.info("timeConstraint 自动规范化: {} -> {}", tcObj, tc);
            params.put("timeConstraint", tc);
        }
    }

    /**
     * P0: orders 参数形态归一化
     * <p>
     * LLM 构造请求时常见的错误形态：
     * <ul>
     *   <li>字符串数组：["{\"metric_time__day\": \"asc\"}"]——每个元素是 JSON 字符串而非对象</li>
     *   <li>整体 JSON 字符串："[{\"metric_time__day\": \"asc\"}]"</li>
     *   <li>单个对象而非数组：{"metric_time__day": "asc"}</li>
     * </ul>
     * 统一归一化为 {@code List<Map<String, String>>}；无法解析时返回可读错误消息
     * （而不是让后续强转抛 ClassCastException）。
     *
     * @return null 表示成功（已写回 params），否则为错误消息
     */
    private String normalizeOrders(Map<String, Object> params) {
        Object value = params.get("orders");
        if (value == null) {
            return null;
        }
        List<Map<String, String>> normalized = new ArrayList<>();
        List<?> items;
        if (value instanceof List<?> list) {
            items = list;
        } else if (value instanceof String s) {
            try {
                items = JSONUtil.parseArray(s);
            } catch (Exception e) {
                // 可能传成了单个对象 {"字段": "asc"}，尝试按单元素解析
                try {
                    normalized.add(toOrderMap(JSONUtil.parseObj(s)));
                    params.put("orders", normalized);
                    return null;
                } catch (Exception e2) {
                    return "orders 参数格式错误：应为对象数组，如 [{\"metric_time__day\": \"asc\"}]，实际传入: " + truncateForError(s);
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            params.put("orders", List.of(toOrderMap(map)));
            return null;
        } else {
            return "orders 参数格式错误：应为对象数组，如 [{\"metric_time__day\": \"asc\"}]，实际为 " + describeType(value);
        }

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof Map<?, ?> map) {
                normalized.add(toOrderMap(map));
            } else if (item instanceof String s) {
                try {
                    normalized.add(toOrderMap(JSONUtil.parseObj(s)));
                    log.info("orders 第 {} 个元素为 JSON 字符串，已自动解析为对象", i + 1);
                } catch (Exception e) {
                    return "orders 第 " + (i + 1) + " 个元素不是合法的排序对象，应为 {\"字段名\": \"asc|desc\"}（如 {\"metric_time__day\": \"asc\"}），实际为: " + truncateForError(s);
                }
            } else {
                return "orders 第 " + (i + 1) + " 个元素应为对象 {\"字段名\": \"asc|desc\"}，实际为 " + describeType(item);
            }
        }
        params.put("orders", normalized);
        return null;
    }

    /** 将任意 Map 转为 {@code Map<String, String>} 排序项 */
    private Map<String, String> toOrderMap(Map<?, ?> map) {
        Map<String, String> order = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            order.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return order;
    }

    /**
     * 类型安全地从参数中提取字符串列表。
     * <p>
     * 元素类型不正确或整体是非法 JSON 字符串时，向 errors 收集可读错误，
     * 而不是让后续 for 循环强转抛 ClassCastException。
     */
    private List<String> extractStringList(Map<String, Object> params, String key, List<String> errors) {
        Object value = params.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof String s) {
                    result.add(s);
                } else {
                    errors.add(key + " 第 " + (i + 1) + " 个元素应为字符串，实际为 " + describeType(item)
                            + "（" + key + " 应为字符串数组，如 [\"metric_time__day\"]）");
                }
            }
            return result;
        }
        if (value instanceof String s) {
            try {
                JSONArray arr = JSONUtil.parseArray(s);
                List<String> result = new ArrayList<>(arr.size());
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof String str) {
                        result.add(str);
                    } else {
                        errors.add(key + " 第 " + (i + 1) + " 个元素应为字符串，实际为 " + describeType(item));
                    }
                }
                return result;
            } catch (Exception e) {
                errors.add(key + " 应为字符串数组，实际传入了无法解析的字符串: " + truncateForError(s));
                return List.of();
            }
        }
        errors.add(key + " 应为字符串数组，实际为 " + describeType(value));
        return List.of();
    }

    /** 描述对象类型，用于可读错误消息 */
    private String describeType(Object o) {
        if (o == null) {
            return "null";
        }
        return o.getClass().getSimpleName();
    }

    /** 截断超长字符串，避免错误消息撑爆上下文 */
    private String truncateForError(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...(截断)";
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

        List<String> metrics = extractStringList(params, "metrics", errors);
        List<String> dimensions = extractStringList(params, "dimensions", errors);
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
        List<String> filters = extractStringList(params, "filters", errors);
        for (String f : filters) {
            if (!f.startsWith("[")) {
                errors.add("筛选条件 '" + f + "' 格式不正确，维度引用应使用方括号，如 [region] IN (\"华东\")");
            }
        }

        // 8. orders 字段检查（normalizeOrders 已保证元素为 Map<String,String>，此处做最终防御）
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
     * <p>
     * 校验策略：维度只要在任一指标的可用维度集中即视为合法（不同指标可拥有不同维度集），
     * 仅当维度不在所有指标的可用集中时才报错。
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

            // 所有指标的可用维度并集
            Set<String> allAvailableDims = metricDimSet.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

            for (String dim : userDims) {
                if (!allAvailableDims.contains(dim)) {
                    errors.add("维度 '" + dim + "' 不在任一指标的可用维度集中，使用该维度将导致查询报错");
                }
            }
        } catch (Exception e) {
            log.error("维度可用性校验查询失败，跳过校验: {}", e.getMessage());
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
     * <p>
     * 相关性校验：只保留与原始关键词有文本重叠（字符级）的术语名和同义词，
     * 过滤掉向量检索返回的"同领域但不同义"的术语（如搜"场内交易客户数"返回"GMV"）。
     * 无任何扩展词通过校验时，仅保留原始关键词，避免无关扩展词干扰检索。
     */
    private List<String> expandKeywordsFromBusinessTerms(String keyword) {
        List<String> keywords = new ArrayList<>();
        keywords.add(keyword);

        try {
            /* 精确命中优先（确定性）：关键词与某术语的 termName/同义词归一化后完全相等时，
             * 该术语与用户输入直接等价，其标准名与全部同义词**无条件**纳入扩展——
             * 不受 topK=3 截断影响，也绕过 isRelevantExpansion 的字符重叠过滤
             * （缩写型同义词如「GMV」↔「成交总额」无中文重叠，旧过滤会拦掉）。 */
            List<BusinessTermSearchResult.TermHit> exactHits = businessTermEsService.exactSearch(keyword, null);
            if (exactHits != null && !exactHits.isEmpty()) {
                for (BusinessTermSearchResult.TermHit hit : exactHits) {
                    if (hit.getTermName() != null && !hit.getTermName().isBlank()
                            && !keywords.contains(hit.getTermName())) {
                        keywords.add(hit.getTermName());
                    }
                    if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
                        for (String syn : hit.getSynonyms().split("[,，;；]")) {
                            String trimmed = syn.trim();
                            if (!trimmed.isEmpty() && !keywords.contains(trimmed)) {
                                keywords.add(trimmed);
                            }
                        }
                    }
                }
                log.info("业务术语精确命中扩展：keyword='{}' → 标准名 {}，同义词已纳入",
                        keyword, exactHits.stream().map(BusinessTermSearchResult.TermHit::getTermName)
                                .collect(Collectors.joining(",")));
            }

            BusinessTermSearchResult termResult = businessTermEsService.hybridSearch(keyword, 3, 0.3);
            if (termResult.getTermHits() != null) {
                for (var hit : termResult.getTermHits()) {
                    // 添加术语标准名（需通过相关性校验）
                    if (hit.getTermName() != null && !hit.getTermName().isBlank()
                            && isRelevantExpansion(keyword, hit.getTermName())) {
                        if (!keywords.contains(hit.getTermName())) {
                            keywords.add(hit.getTermName());
                        }
                    }
                    // 添加同义词（需通过相关性校验）
                    if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
                        for (String syn : hit.getSynonyms().split("[,，;；]")) {
                            String trimmed = syn.trim();
                            if (!trimmed.isEmpty() && !trimmed.equals(keyword)
                                    && isRelevantExpansion(keyword, trimmed)
                                    && !keywords.contains(trimmed)) {
                                keywords.add(trimmed);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("业务术语扩展检索失败，使用原始关键词: {}", e.getMessage());
        }

        // 去重并限制最多5个关键词，避免过多检索调用
        return keywords.stream().distinct().limit(5).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 判断扩展词是否与原始关键词相关（非语义漂移的同领域术语）。
     * <p>
     * 校验规则：扩展词与原始关键词的字符重叠率 ≥ 0.4 才认为相关。
     * 重叠率 = 交集大小 / min(原词字符数, 扩展词字符数)，取 min 分母避免短词被长词轻易覆盖。
     * <p>
     * 例如：
     * <ul>
     *   <li>"保费" vs "保险费" → 交集{保}=1, min(2,3)=2, 0.5 ≥ 0.4 → 相关 ✓</li>
     *   <li>"保费" vs "保费收入" → 交集{保,费}=2, min(2,4)=2, 1.0 ≥ 0.4 → 相关 ✓</li>
     *   <li>"保费" vs "场内交易客户数" → 交集=0 → 不相关 ✗</li>
     *   <li>"保费" vs "AUM净流入贡献率" → 无中文重叠 → 不相关 ✗</li>
     * </ul>
     * <p>
     * 旧逻辑仅判断"是否有任意一个字符重叠"，单字重叠（如"保"）即通过，导致同领域但不同义的
     * 术语被过度扩展，should 子句加分稀释了原始关键词的精确匹配优势。
     */
    private boolean isRelevantExpansion(String original, String expanded) {
        if (original == null || expanded == null) {
            return false;
        }
        // 提取中文字符集合
        Set<String> origChars = extractChineseChars(original);
        Set<String> expChars = extractChineseChars(expanded);
        // 中文字符重叠率 ≥ 0.4 才认为相关
        if (!origChars.isEmpty() && !expChars.isEmpty()) {
            Set<String> intersection = new HashSet<>(origChars);
            intersection.retainAll(expChars);
            if (!intersection.isEmpty()) {
                int denominator = Math.min(origChars.size(), expChars.size());
                double overlapRatio = (double) intersection.size() / denominator;
                return overlapRatio >= 0.4;
            }
        }
        // 提取英文单词集合（下划线分隔）
        Set<String> origWords = extractEnglishWords(original);
        Set<String> expWords = extractEnglishWords(expanded);
        if (!origWords.isEmpty() && !expWords.isEmpty()) {
            origWords.retainAll(expWords);
            return !origWords.isEmpty();
        }
        return false;
    }

    /** 提取字符串中的所有中文字符 */
    private static Set<String> extractChineseChars(String text) {
        Set<String> chars = new HashSet<>();
        if (text == null) {
            return chars;
        }
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chars.add(String.valueOf(c));
            }
        }
        return chars;
    }

    /** 提取字符串中的英文单词（按下划线和非字母数字分隔） */
    private static Set<String> extractEnglishWords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) {
            return words;
        }
        String[] parts = text.split("[^a-zA-Z0-9]+");
        for (String part : parts) {
            if (!part.isEmpty() && part.matches(".*[a-zA-Z].*")) {
                words.add(part.toLowerCase());
            }
        }
        return words;
    }

    /**
     * 归一化字符串用于「标点/空白不敏感」的匹配比较。
     * <p>
     * 全角 ASCII 字母数字转半角、统一小写，仅保留字母 / 数字 / 中文字符，
     * 丢弃所有标点、括号、连接符（-、_）、斜杠、空白等分隔符。
     * 例：{@code "销售金额（含税）"} → {@code "销售金额含税"}；
     *     {@code "sales_amount"} → {@code "salesamount"}；
     *     {@code "metric_time__month"} → {@code "metrictimemonth"}。
     * <p>
     * 背景：用户问句常带全角括号、半角括号、连接符等标点，LLM 生成检索 keyword
     * 时可能截断 / 改写 / 转换全角半角，导致「按关键词匹配指标名称」失配。此方法只用于
     * 「是否匹配」的判定；判定命中后仍使用原始 metricName / 展示名构造查询，不改写查询值。
     * 实现委托 {@link vip.mate.dataagent.support.NameMatchSupport}，与业务术语/维度
     * MySQL 降级检索的标点不敏感 LIKE 模式共用同一套字符归一化规则。
     */
    private static String normalizeKey(String text) {
        return NameMatchSupport.normalizeKey(text);
    }

    /**
     * 获取当前会话的用户原始消息。
     * <p>
     * 通过 ToolExecutionContext 获取 conversationId，再从 scopeContext 读取用户原始消息。
     * 用于补充检索关键词，防止 LLM 精简丢失关键限定信息。
     */
    private String getOriginalMessage() {
        String conversationId = ToolExecutionContext.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return scopeContext.getOriginalMessage(conversationId);
    }

    /**
     * 提取用户原话中未被 LLM keyword 覆盖的差集片段。
     * <p>
     * 策略一（子串匹配）：仅在 keyword 是原话子串时生效，从原话中移除 keyword 部分，剩余即为差集。
     * 差集保留了"整体""汇总"等 LLM 丢弃的限定词，同时避免注入整句导致多指标提问串味。
     * <p>
     * 策略二（分词级匹配）：当子串匹配失败时（如 LLM 改写了同义词"市场占有率"→"市占率"），
     * 降级为中文字符集合的子集判断：如果 keyword 的中文字符集合是原话中文字符集合的子集
     * （覆盖率 > 60%），则认为 keyword 是原话的改写，提取差集中文字符重组为差集片段。
     * <p>
     * 示例：
     * <ul>
     *   <li>原话="交易市占率（整体）", keyword="交易市占率" → 子串匹配 → 差集="（整体）"</li>
     *   <li>原话="市场占有率（整体）", keyword="市占率" → 子串不匹配 → 分词级：keyword 字符{市,占,率}⊂{市,场,占,有,率,整,体} 覆盖率 3/7=43%<60% → 不触发（避免误匹配）</li>
     *   <li>原话="保费收入（汇总）", keyword="保费收入" → 子串匹配 → 差集="（汇总）"</li>
     *   <li>原话="帮我看下保费收入", keyword="场内交易客户数" → 子串不匹配 → 分词级：keyword 字符{场,内,交,易,客,户,数} 与原话{帮,我,看,下,保,费,收,入} 无交集 → 不触发</li>
     * </ul>
     *
     * @param originalMessage 用户原始消息
     * @param keyword         LLM 传入的检索关键词
     * @return 差集片段；无法提取时返回 null
     */
    private static String extractKeywordComplement(String originalMessage, String keyword) {
        // 策略一：子串匹配（原逻辑）
        if (originalMessage.contains(keyword)) {
            String complement = originalMessage.replace(keyword, "").trim();
            return complement.isEmpty() ? null : complement;
        }

        // 策略二：分词级匹配（中文字符集合子集判断）
        Set<String> origChars = extractChineseChars(originalMessage);
        Set<String> kwChars = extractChineseChars(keyword);

        if (origChars.isEmpty() || kwChars.isEmpty()) {
            return null;
        }

        // 计算 keyword 中文字符在原话中的覆盖率
        Set<String> kwCharsCopy = new HashSet<>(kwChars);
        kwCharsCopy.retainAll(origChars);
        double coverage = (double) kwCharsCopy.size() / kwChars.size();

        // 覆盖率阈值：keyword 的中文字符至少 60% 出现在原话中，才认为是改写而非完全不相关
        if (coverage < 0.6) {
            return null;
        }

        // 提取差集：原话中不在 keyword 字符集合中的中文字符
        Set<String> diffChars = new HashSet<>(origChars);
        diffChars.removeAll(kwChars);

        if (diffChars.isEmpty()) {
            return null;
        }

        // 从原话中按顺序提取差集字符，保持原始语序
        StringBuilder diff = new StringBuilder();
        for (char c : originalMessage.toCharArray()) {
            String ch = String.valueOf(c);
            if (diffChars.contains(ch)) {
                diff.append(c);
            }
        }

        String result = diff.toString().trim();
        return result.isEmpty() ? null : result;
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
            log.error("查询维度关联指标失败: {}", e.getMessage());
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
    /**
     * 自动注入唯一 Aloudata 数据源。
     * <p>
     * 权限收紧：仅从当前用户「可见且已启用」的 Aloudata 源中选择；无权限的数据源不参与自动注入。
     */
    private Long autoResolveDatasourceId() {
        try {
            List<DatasourceVO> visibleAloudata = visibleEnabledDatasources().stream()
                    .filter(d -> d.getSourceType() != null
                            && DataAgentConstants.SOURCE_TYPE_ALOUDATA.equalsIgnoreCase(d.getSourceType()))
                    .toList();
            if (visibleAloudata.size() == 1) {
                Long id = visibleAloudata.get(0).getId();
                log.info("自动注入当前用户可见的唯一 Aloudata 数据源 ID: {}", id);
                return id;
            }
        } catch (Exception e) {
            log.error("自动查找 Aloudata 数据源失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 语义检索的数据源解析：确定性优先，不盲信 LLM 猜测的 datasourceId。
     * <p>
     * 顺序：
     * <ol>
     *   <li>用户已勾选数据源（白名单）→ 白名单权威（单值自动注入 / 多值校验），直接采用；</li>
     *   <li>无白名单 + 全局唯一启用的 Aloudata 源 → 确定性采用该源，忽略 LLM 猜测值
     *       （本工具面向 Aloudata，唯一源时无需也不应让 LLM 猜 datasourceId）；</li>
     *   <li>无白名单 + 多个 / 零个 Aloudata 源 → 校验 LLM 传值确为"存在且启用"的数据源，合法则采用，
     *       非法（不存在 / 已禁用 / 为空）则返回可用数据源列表，引导 LLM 从中重选，而非静默用错。</li>
     * </ol>
     */
    private ScopeResolveResult<Long> resolveAloudataDatasourceId(String convId, Long llmId) {
        ScopeResolveResult<Long> scope = scopeContext.resolveDatasourceId(convId, llmId);
        if (scope.hasError()) {
            return scope;
        }
        // 用户已勾选 → 白名单权威
        if (scopeContext.hasScope(convId)) {
            return scope;
        }
        Long resolved = scope.getResolvedValue();
        // 无白名单：唯一启用的 Aloudata 源 → 确定性采用，忽略 LLM 猜测值
        Long onlyAloudata = autoResolveDatasourceId();
        if (onlyAloudata != null) {
            if (resolved != null && !resolved.equals(onlyAloudata)) {
                log.info("语义检索：忽略 LLM 猜测的 datasourceId={}，采用唯一 Aloudata 数据源 {}", resolved, onlyAloudata);
            }
            return ScopeResolveResult.ok(onlyAloudata);
        }
        // 多个 / 零个 Aloudata 源：校验 LLM 传值确为当前用户「可见且已启用」的数据源
        if (resolved != null && isVisibleEnabledDatasource(resolved)) {
            return ScopeResolveResult.ok(resolved);
        }
        log.info("语义检索：LLM 传入的 datasourceId={} 不存在、已禁用或无访问权限，返回可用数据源列表引导重选", resolved);
        return ScopeResolveResult.fail(buildDatasourceGuide());
    }

    /**
     * 构建"可用数据源列表"引导文本，供无法确定 datasourceId 时返回给 LLM 重选。
     * <p>
     * 权限收紧：仅列出当前用户「可见且已启用」的数据源，不暴露无权限的数据源。
     */
    private String buildDatasourceGuide() {
        try {
            List<DatasourceVO> sources = datasourceManageService.listVisibleEnabledDatasources();
            if (sources.isEmpty()) {
                return "未找到您有权限访问的已启用数据源，请先在数据源页面配置或授权后再检索。";
            }
            StringBuilder sb = new StringBuilder("需要指定有效的 datasourceId（请勿猜测）。当前您可用的数据源：\n");
            for (DatasourceVO s : sources) {
                sb.append("- datasourceId=").append(s.getId())
                        .append(", name=").append(s.getName() != null ? s.getName() : "")
                        .append(", type=").append(s.getSourceType() != null ? s.getSourceType() : "")
                        .append("\n");
            }
            sb.append("请从上述列表中选择与用户问题匹配的 datasourceId 后重新调用。");
            return sb.toString();
        } catch (Exception e) {
            log.error("构建可用数据源列表失败: {}", e.getMessage());
            return "需要指定有效的 datasourceId（请勿猜测）。";
        }
    }

    /**
     * 当前用户「可见且已启用」的数据源列表（权限感知），供兜底解析 / 校验使用。
     * 用户上下文缺失时服务层回退为全量已启用列表。
     */
    private List<DatasourceVO> visibleEnabledDatasources() {
        try {
            return datasourceManageService.listVisibleEnabledDatasources();
        } catch (Exception e) {
            log.error("获取当前用户可见数据源失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 指定数据源是否对当前用户「可见且已启用」（权限收紧：无权限不可用）。
     */
    private boolean isVisibleEnabledDatasource(Long datasourceId) {
        if (datasourceId == null) {
            return false;
        }
        return visibleEnabledDatasources().stream().anyMatch(d -> datasourceId.equals(d.getId()));
    }

    /**
     * 当 LLM 传入的 datasourceId 被确定性解析重定向到其他数据源时，在结果前附加提示。
     * <p>
     * 背景：无白名单且全局唯一 Aloudata 源时，工具会忽略 LLM 猜测的 datasourceId 并静默改用唯一源，
     * 但返回结果中只出现实际使用的 ID。LLM 会误以为自己在"另一个数据源上做对照测试"（metadata
     * 观测中该误判出现 3 次），浪费大量检索调用。显式告知可消除歧义。
     */
    private String prependDatasourceRedirectNote(Long requestedId, Long resolvedId, String result) {
        if (requestedId == null || requestedId.equals(resolvedId) || result == null) {
            return result;
        }
        DatasourceEntity entity = datasourceMapper.selectById(resolvedId);
        String name = entity != null && entity.getName() != null ? entity.getName() : String.valueOf(resolvedId);
        return "⚠️ 你传入的 datasourceId=" + requestedId + " 已自动重定向到唯一可用 Aloudata 数据源（datasourceId="
                + resolvedId + "，名称: " + name + "），以下检索结果均来自该数据源。\n" + result;
    }

    // ==================== API 错误智能解析 ====================

    /**
     * 从失败响应体中提取错误消息。
     * <p>
     * 远程 API 的错误字段不统一（errorMsg/detailErrorMsg/message/msg/error/errorMessage/code 等），
     * 逐个尝试常见字段，全部为空时返回 null（由调用方转储完整响应体）。
     */
    private String extractApiErrorMessage(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String[] candidateKeys = {"errorMsg", "detailErrorMsg", "message", "msg",
                "error", "errorMessage", "error_desc", "desc"};
        for (String key : candidateKeys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank() && !"null".equalsIgnoreCase(String.valueOf(v))) {
                return String.valueOf(v);
            }
        }
        // code 字段兜底：部分版本仅用 code 表达错误
        Object code = body.get("code");
        if (code != null && !String.valueOf(code).isBlank() && !"null".equalsIgnoreCase(String.valueOf(code))) {
            return "code=" + code;
        }
        return null;
    }

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
            if (entry.getValue() instanceof List<?> colValues) {
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
                if (entry.getValue() instanceof List<?> colValues) {
                    if (i < colValues.size()) {
                        Object cell = colValues.get(i);
                        // 提取 value 字段（ColumnValue 格式）
                        if (cell instanceof Map) {
                            row.put(colName, ((Map<String, Object>) cell).get("value"));
                        } else {
                            row.put(colName, cell);
                        }
                    } else {
                        // 列长度不一致时填充 null，避免行数据字段缺失
                        row.put(colName, null);
                    }
                }
            }
            rows.add(row);
        }
        return rows;
    }

    // ==================== 确定性族级口径兜底 ====================

    /**
     * 确定性族级兜底（①②③）。
     * <p>
     * 背景：形如「交易市占率（整体）/（个人）/（机构）」的同基名多口径指标族，当 LLM 把 keyword
     * 精简为基名「交易市占率」时，ES 打分会让全族并列、再被 TopK 截断，用户真正想要的口径可能被挤出
     * 结果（偶现检索不到）。此方法用确定性手段兜底，不依赖 ES 排序：
     * <ol>
     *   <li>① 按基名（keyword 剥离尾部括号口径）精确捞全整族（LIKE 基名% + 剥离后精确等于基名的校验，
     *       排除仅前缀相同的其它指标如「交易市占率排名（整体）」），并把结果中缺失的族成员补入
     *       mergedMetrics，保证整族必然出现、不受 TopK 影响；</li>
     *   <li>② 用「原始用户消息 + keyword」精确匹配各成员的口径后缀：唯一命中则判定为目标口径，置为
     *       最高分使其稳居首位；</li>
     *   <li>③ 0 或多命中时不自行选择，返回族信息由 {@link #appendFamilyHint} 提示消歧。</li>
     * </ol>
     * 仅当基名对应 ≥2 个口径成员时才视为「指标族」并生效；单指标场景直接返回、不干扰原有流程。
     * <p>
     * 局限：基名从 keyword（通常中文）剥离得到，若 LLM 直接传英文 metricName 则不触发族兜底（英文名
     * 通常已是精确命中，不属于本 bug 的失败模式）；口径后缀仅识别「尾部括号」这一常见命名约定。
     *
     * @param datasourceId    数据源 ID
     * @param keyword         LLM 传入的检索关键词
     * @param originalMessage 用户原始消息（可能为 null）
     * @param mergedMetrics   当前指标命中列表（可变，方法内可能注入缺失族成员并重排）
     * @return 族兜底结果；未触发时 triggered() 为 false
     */
    private FamilyBackfillResult backfillMetricFamily(Long datasourceId, String keyword,
                                                      String originalMessage,
                                                      List<AloudataSearchResult.MetricHit> mergedMetrics) {
        FamilyBackfillResult none = new FamilyBackfillResult(null, List.of(), List.of());
        if (datasourceId == null || keyword == null || keyword.isBlank()) {
            return none;
        }
        String base = stripTrailingCaliber(keyword);
        if (base.isEmpty()) {
            return none;
        }
        try {
            // ① 按基名捞候选
            List<AloudataMetricEntity> candidates = metricMapper.selectList(
                    new LambdaQueryWrapper<AloudataMetricEntity>()
                            .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                            .likeRight(AloudataMetricEntity::getMetricDisplayName, base)
                            .select(AloudataMetricEntity::getMetricName,
                                    AloudataMetricEntity::getMetricDisplayName,
                                    AloudataMetricEntity::getType,
                                    AloudataMetricEntity::getBusinessCaliber,
                                    AloudataMetricEntity::getMetricCategoryName,
                                    AloudataMetricEntity::getUnit,
                                    AloudataMetricEntity::getSynonyms)
                            // ORDER BY 保证 LIMIT 截断确定（极端前缀 >200 时才可能漏，真实指标族远小于此）
                            .orderByAsc(AloudataMetricEntity::getMetricDisplayName)
                            .last("LIMIT " + FAMILY_LOOKUP_MAX));
            // ② 聚合族成员。keyword 自带口径后缀（如"交易市占率（整体）"）时只收"基名+口径"精确族
            //    （原逻辑，避免"交易市占率分析"等前缀相似的指标混入）；keyword 为宽泛词（如"保费"）时
            //    按前缀聚合（保费收入/保费支出/人均保费...），解决宽泛查询下目标指标被 topK 截断排除的问题
            boolean keywordHasCaliber = !base.equals(keyword);
            // 宽泛词聚合防御：基名过短（如单字"保"）时前缀匹配面过宽，族列表可能爆炸，
            // 交给原有 ES 流程而非聚合兜底
            if (!keywordHasCaliber && base.length() < 2) {
                return none;
            }
            List<AloudataMetricEntity> familyMembers = candidates.stream()
                    .filter(m -> {
                        String stripped = stripTrailingCaliber(m.getMetricDisplayName());
                        return keywordHasCaliber ? base.equals(stripped) : stripped.startsWith(base);
                    })
                    .toList();

            // ③ 触发条件：
            //    口径族（keyword 带口径后缀）：少于 2 个成员不是"多口径族"，无歧义，交给原有 ES 流程
            //    宽泛词聚合（keyword 无口径后缀）：至少有 1 个前缀族成员即触发，唯一成员也补入，
            //    防止该成员被 ES 打分 + topK 截断排除
            if (keywordHasCaliber ? familyMembers.size() < 2 : familyMembers.isEmpty()) {
                return none;
            }

            // ② 口径识别：用「原话 + keyword」匹配各成员。
            // 优先"强匹配"：
            //   a) 整名命中（含标点不敏感）——原话/关键词去掉标点后完整包含该成员展示名，直接视为强命中。
            //      这是「-」连接成员的唯一识别通道：如 展示名「期货收入-应收-当年」无括号口径，
            //      extractTrailingCaliber 取不到口径词，但 normalizeKey(原话) 含 normalizeKey(展示名) 即可锁定。
            //   b) 括号口径命中（原话含完整展示名 / base+口径 / 括号包裹口径），可精确区分互为子串的口径
            //      （如"个人"vs"个人及机构"，括号形式"（个人）"不会命中"（个人及机构）"）；
            // 无强匹配时用"弱匹配"（口径 token 出现在原话任意处）兜底，再用子串去重（longest-wins）收敛。
            String probe = (originalMessage != null ? originalMessage : "") + " " + keyword;
            String normProbe = normalizeKey(probe);
            List<AloudataMetricEntity> strong = new ArrayList<>();
            List<AloudataMetricEntity> loose = new ArrayList<>();
            for (AloudataMetricEntity m : familyMembers) {
                String display = m.getMetricDisplayName() != null ? m.getMetricDisplayName() : "";
                // a) 整名命中（标点不敏感）：覆盖 全角 vs 半角括号、-、_、空白差异
                //    （如 展示名「销售金额（含税）/期货收入-应收-当年」命中 原话中的对应写法或 LLM 精简后形态）
                if (!display.isEmpty() && normProbe.contains(normalizeKey(display))) {
                    strong.add(m);
                    continue;
                }
                String caliber = extractTrailingCaliber(display);
                if (caliber.isEmpty()) {
                    continue;
                }
                boolean strongHit = probe.contains(base + caliber)
                        || probe.contains("（" + caliber + "）")
                        || probe.contains("(" + caliber + ")");
                if (strongHit) {
                    strong.add(m);
                }
                if (probe.contains(caliber)) {
                    loose.add(m);
                }
            }
            List<AloudataMetricEntity> matched = dropSubstringDominatedCalibers(strong.isEmpty() ? loose : strong);
            // 宽泛词聚合且整族唯一：成员无括号口径，口径匹配必然落空；唯一成员即目标，直接视为命中
            if (matched.isEmpty() && familyMembers.size() == 1) {
                matched = familyMembers;
            }
            // 保证整族都在结果中：补入缺失成员（不受 TopK 影响）
            Set<String> present = mergedMetrics.stream()
                    .map(AloudataSearchResult.MetricHit::getMetricName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            double maxScore = mergedMetrics.stream()
                    .mapToDouble(AloudataSearchResult.MetricHit::getScore)
                    .max().orElse(1.0);
            for (AloudataMetricEntity m : familyMembers) {
                if (m.getMetricName() != null && !present.contains(m.getMetricName())) {
                    mergedMetrics.add(toMetricHit(m, maxScore));
                    present.add(m.getMetricName());
                }
            }

            // 为补入的族成员补充可用维度（ES 命中项已在检索层补充；补入项否则会缺失"可用维度"，
            // 而被唯一命中的目标口径往往正是补入项，缺维度会让 LLM 误判其无可用维度或多一次工具调用）
            enrichBackfilledDimensions(datasourceId, mergedMetrics);

            // 命中口径（唯一命中或多选，如"对比整体和个人"）：置为最高分，稳居前列
            if (!matched.isEmpty()) {
                double dominant = maxScore + 1.0;
                Set<String> matchedNames = matched.stream()
                        .map(AloudataMetricEntity::getMetricName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                for (AloudataSearchResult.MetricHit hit : mergedMetrics) {
                    if (matchedNames.contains(hit.getMetricName())) {
                        hit.setScore(dominant);
                    }
                }
            }

            // 注入 / 改分后重排
            mergedMetrics.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

            return new FamilyBackfillResult(base, familyMembers, matched);
        } catch (Exception e) {
            log.error("族级兜底失败，跳过（不影响原有检索）: {}", e.getMessage());
            return none;
        }
    }

    /**
     * 追加确定性族级口径提示。
     * <ul>
     *   <li>唯一命中：明确告知目标 metricName，指示直接使用；</li>
     *   <li>未唯一命中：列出整族全部口径，指示向用户确认、禁止自行选择。</li>
     * </ul>
     * 该信息来自元数据精确查询，不受检索排序 / TopK 影响。
     */
    private void appendFamilyHint(StringBuilder sb, FamilyBackfillResult family, String keyword) {
        if (family == null || !family.triggered()) {
            return;
        }
        List<AloudataMetricEntity> matched = family.matched();

        // 唯一命中：明确目标 metricName，指示直接使用
        if (matched.size() == 1) {
            AloudataMetricEntity r = matched.get(0);
            String caliber = extractTrailingCaliber(r.getMetricDisplayName());
            boolean isFamily = family.family().size() >= 2;
            sb.append("## ✅ 指标口径确定性识别\n\n");
            if (isFamily) {
                // 多口径族场景：按用户原话精确匹配到唯一口径/成员
                if (caliber.isEmpty()) {
                    sb.append("「").append(family.baseName()).append("」是多口径指标族，已按用户原话精确匹配到唯一成员，目标指标：**")
                            .append(r.getMetricName()).append("**(")
                            .append(r.getMetricDisplayName() != null ? r.getMetricDisplayName() : "").append(")\n");
                } else {
                    sb.append("「").append(family.baseName()).append("」是多口径指标族，已按用户原话精确匹配口径「").append(caliber)
                            .append("」，目标指标：**").append(r.getMetricName()).append("**(")
                            .append(r.getMetricDisplayName() != null ? r.getMetricDisplayName() : "").append(")\n");
                }
            } else {
                // 宽泛词聚合场景：基名下仅有 1 个指标，直接视为目标
                sb.append("「").append(family.baseName()).append("」相关指标仅有 1 个，目标指标：**")
                        .append(r.getMetricName()).append("**(")
                        .append(r.getMetricDisplayName() != null ? r.getMetricDisplayName() : "").append(")\n");
            }
            sb.append("> 该结果来自元数据精确匹配，不受检索排序 / TopK 影响。请直接使用此 metricName 构造查询；若与用户意图不符再追问。\n\n");
            return;
        }

        // 多命中：用户显式提到多个口径（如"对比整体和个人"），全部为目标，无需追问
        if (matched.size() >= 2) {
            sb.append("## ✅ 指标口径确定性识别（多口径）\n\n");
            sb.append("「").append(family.baseName()).append("」是多口径指标族，用户原话提到多个口径，以下均为目标口径（可分别查询或对比）：\n");
            for (AloudataMetricEntity m : matched) {
                sb.append("  - **").append(m.getMetricName()).append("**(")
                        .append(m.getMetricDisplayName() != null ? m.getMetricDisplayName() : "").append(")\n");
            }
            sb.append("> 以上来自元数据精确匹配，不受检索排序 / TopK 影响。请直接使用这些 metricName。\n\n");
            return;
        }

        // 0 命中：无法唯一确定目标（多口径族无匹配，或宽泛词聚合场景），列出全部候选让用户确认
        sb.append("## ⚠️ 指标消歧（确定性）\n\n");
        sb.append("「").append(family.baseName())
                .append("」相关指标较多，无法从用户输入唯一确定目标，**请勿自行选择，向用户确认后再查询**。")
                .append("相关指标（来自元数据精确查询，不受检索排序影响）：\n");
        for (AloudataMetricEntity m : family.family()) {
            sb.append("  - **").append(m.getMetricName()).append("**(")
                    .append(m.getMetricDisplayName() != null ? m.getMetricDisplayName() : "").append(")");
            if (m.getBusinessCaliber() != null && !m.getBusinessCaliber().isBlank()) {
                sb.append(" — ").append(m.getBusinessCaliber());
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    /**
     * 为族级兜底补入的成员补充可用维度。
     * <p>
     * ES 命中项的 availableDimensions 已由检索层填充（可能为空列表），补入项则为 null；此处仅处理
     * availableDimensions 为 null 的补入项，一次批量查询指标-维度关联表，避免逐个查询。
     */
    private void enrichBackfilledDimensions(Long datasourceId, List<AloudataSearchResult.MetricHit> hits) {
        List<String> names = hits.stream()
                .filter(h -> h.getAvailableDimensions() == null && h.getMetricName() != null)
                .map(AloudataSearchResult.MetricHit::getMetricName)
                .distinct()
                .toList();
        if (names.isEmpty()) {
            return;
        }
        try {
            List<AloudataMetricDimensionEntity> relations = metricDimensionMapper.selectList(
                    new LambdaQueryWrapper<AloudataMetricDimensionEntity>()
                            .eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId)
                            .in(AloudataMetricDimensionEntity::getMetricName, names)
                            .select(AloudataMetricDimensionEntity::getMetricName,
                                    AloudataMetricDimensionEntity::getDimName));
            Map<String, List<String>> dimMap = relations.stream()
                    .collect(Collectors.groupingBy(
                            AloudataMetricDimensionEntity::getMetricName,
                            Collectors.mapping(AloudataMetricDimensionEntity::getDimName, Collectors.toList())));
            for (AloudataSearchResult.MetricHit h : hits) {
                if (h.getAvailableDimensions() == null && h.getMetricName() != null) {
                    h.setAvailableDimensions(dimMap.getOrDefault(h.getMetricName(), List.of()));
                }
            }
        } catch (Exception e) {
            log.error("族级兜底补入成员的维度补充失败，跳过: {}", e.getMessage());
        }
    }

    /** 由指标实体构造检索命中项（用于族级兜底补入缺失成员） */
    private AloudataSearchResult.MetricHit toMetricHit(AloudataMetricEntity m, double score) {
        AloudataSearchResult.MetricHit hit = new AloudataSearchResult.MetricHit();
        hit.setMetricName(m.getMetricName());
        hit.setMetricDisplayName(m.getMetricDisplayName());
        hit.setType(m.getType());
        hit.setBusinessCaliber(m.getBusinessCaliber());
        hit.setSynonyms(m.getSynonyms());
        hit.setCategoryName(m.getMetricCategoryName());
        hit.setUnit(m.getUnit());
        hit.setScore(score);
        hit.setMatchSource("family_backfill");
        return hit;
    }

    /** 剥离指标展示名尾部的口径后缀（如「交易市占率（整体）」→「交易市占率」） */
    private static String stripTrailingCaliber(String name) {
        if (name == null) {
            return "";
        }
        return TRAILING_CALIBER_PATTERN.matcher(name.trim()).replaceAll("").trim();
    }

    /** 提取指标展示名尾部口径后缀内容（如「交易市占率（整体）」→「整体」），无则返回空串 */
    private static String extractTrailingCaliber(String name) {
        if (name == null) {
            return "";
        }
        Matcher matcher = TRAILING_CALIBER_CONTENT_PATTERN.matcher(name.trim());
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /**
     * 口径去重（longest-wins）：去掉口径是另一命中口径真子串的成员。
     * <p>
     * 例：同时命中「个人」与「个人及机构」时，「个人」仅因是「个人及机构」的子串而命中，保留更具体的
     * 「个人及机构」。用于弱匹配兜底后收敛，避免互为子串的口径造成误判为多口径。
     * <p>
     * 2025 增补（覆盖「-」连接族）：整名命中（标点不敏感）会让无括号口径的短名成员与其更长兄弟同时命中，
     * 如 命中集={期货收入, 期货收入-应收-当年}——此时两个成员的 extractTrailingCaliber 都为空，
     * 旧口径子串规则不生效。改用「归一化展示名的包含关系」做 longest-wins：展示名（去标点）是另一成员
     * 展示名（去标点）真子串且更短时，判定为被支配丢弃，只保留最具体的成员，避免把短名指标误判成
     * "用户提到了多个口径"。
     */
    private List<AloudataMetricEntity> dropSubstringDominatedCalibers(List<AloudataMetricEntity> matched) {
        if (matched.size() <= 1) {
            return matched;
        }
        List<AloudataMetricEntity> result = new ArrayList<>();
        for (AloudataMetricEntity m : matched) {
            String cal = extractTrailingCaliber(m.getMetricDisplayName());
            String normName = normalizeKey(m.getMetricDisplayName());
            boolean dominated = false;
            for (AloudataMetricEntity other : matched) {
                if (other == m) {
                    continue;
                }
                String otherCal = extractTrailingCaliber(other.getMetricDisplayName());
                // 口径子串支配仅对非空口径生效：cal 为空时 String.contains("") 恒为 true，
                // 会让无括号口径成员（如「期货收入-应收-当年」）被任何带括号口径的成员误删；
                // 空口径成员的交叠关系由下方 nameDominated（归一化展示名 longest-wins）处理
                boolean caliberDominated = !cal.isEmpty() && !cal.equals(otherCal) && otherCal.contains(cal);
                // 归一化展示名包含关系 longest-wins：仅当 other 更长且包含当前成员时才支配，
                // 等长（如 全角 vs 半角括号两种写法）不互相支配，保留交给多口径流程
                String otherNorm = normalizeKey(other.getMetricDisplayName());
                boolean nameDominated = !normName.isEmpty() && otherNorm.length() > normName.length()
                        && otherNorm.contains(normName);
                if (caliberDominated || nameDominated) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                result.add(m);
            }
        }
        return result;
    }

    // ==================== 通用口径重排 ====================

    /**
     * 通用口径重排：当族级括号兜底未触发时，用"用户原话 vs 展示名"的字符重叠度做排序调整。
     * <p>
     * 覆盖括号族兜底无法处理的形态：
     * <ul>
     *   <li>前缀式：个人交易市占率 / 机构交易市占率（口径词在基名前面）</li>
     *   <li>修饰词式：华东区销售额 / 华南区销售额（地域等修饰词嵌入名称）</li>
     *   <li>换序式：交易市占率整体（无括号包裹）</li>
     * </ul>
     * <p>
     * 算法：
     * <ol>
     *   <li>对每个候选指标，计算 overlap(用户原话, 展示名) = 两者中文字符交集大小 / 展示名中文字符总数；</li>
     *   <li>找到重叠度最高的候选（bestOverlap），若 bestOverlap > 阈值 且 比当前首名的重叠度高出 0.1 以上
     *       （避免微小差距导致频繁重排），则将其分数提升到首名之上、并重排。</li>
     * </ol>
     * <p>
     * 安全性：只在原话与 keyword 不同时生效（原话 == keyword 说明 LLM 未丢失信息，无需重排）；
     * 重排只改变分数和顺序，不增删结果项，不影响原有 ES 召回。
     *
     * @param originalMessage 用户原始消息（可能为 null）
     * @param keyword         LLM 传入的检索关键词
     * @param mergedMetrics   当前指标命中列表（可变，方法内可能调整分数和顺序）
     */
    private void applyGenericCaliberRerank(String originalMessage, String keyword,
                                            List<AloudataSearchResult.MetricHit> mergedMetrics) {
        if (originalMessage == null || originalMessage.equals(keyword) || mergedMetrics.size() < 2) {
            return;
        }
        try {
            // 计算每个候选与用户原话的字符重叠度
            Set<String> origChars = extractChineseChars(originalMessage);
            if (origChars.isEmpty()) {
                return;
            }
            double bestOverlap = 0;
            int bestIndex = -1;
            double topOverlap = 0;
            for (int i = 0; i < mergedMetrics.size(); i++) {
                String displayName = mergedMetrics.get(i).getMetricDisplayName();
                if (displayName == null || displayName.isBlank()) {
                    continue;
                }
                Set<String> nameChars = extractChineseChars(displayName);
                if (nameChars.isEmpty()) {
                    continue;
                }
                double overlap = computeCharOverlap(origChars, nameChars);
                if (i == 0) {
                    topOverlap = overlap;
                }
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    bestIndex = i;
                }
            }

            // 重排条件：最佳重叠度超过阈值，且比当前首名高出 0.1 以上（避免微弱差距导致频繁重排）
            if (bestIndex > 0 && bestOverlap >= GENERIC_RERANK_THRESHOLD
                    && bestOverlap - topOverlap >= 0.1) {
                double dominant = mergedMetrics.get(0).getScore() + 0.5;
                mergedMetrics.get(bestIndex).setScore(dominant);
                mergedMetrics.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                log.info("通用口径重排：'{}' 与展示名重叠度 {} 高于首名 {}，已提权到首位",
                        originalMessage, String.format("%.2f", bestOverlap), String.format("%.2f", topOverlap));
            }
        } catch (Exception e) {
            log.error("通用口径重排失败，跳过（不影响原有检索）: {}", e.getMessage());
        }
    }

    /**
     * 计算两个中文字符集合的重叠度 = 交集大小 / nameChars 大小。
     * <p>
     * 语义：展示名中有多少比例的字符出现在用户原话中。
     * 值域 [0, 1]，1 表示展示名的每个字符都在原话中出现。
     *
     * @param origChars 用户原话的中文字符集合
     * @param nameChars 展示名的中文字符集合
     * @return 重叠度
     */
    private static double computeCharOverlap(Set<String> origChars, Set<String> nameChars) {
        if (nameChars.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(nameChars);
        intersection.retainAll(origChars);
        return (double) intersection.size() / nameChars.size();
    }

    /**
     * 族级兜底结果。
     *
     * @param baseName 指标族基名；未触发时为 null
     * @param family   整族成员；未触发时为空。
     *                 口径族场景 ≥2；宽泛词聚合场景可能 =1（唯一前缀成员即目标）。
     * @param resolved 由用户原话唯一确定的目标口径指标；未唯一确定时为空
     */
    private record FamilyBackfillResult(String baseName,
                                        List<AloudataMetricEntity> family,
                                        List<AloudataMetricEntity> matched) {
        /**
         * 是否触发族级兜底。
         * <p>
         * family 非空即触发：宽泛词聚合场景下 family 可能只有 1 个成员，
         * 此时该成员是目标指标、已设置 dominant 分排首位，需要 appendFamilyHint
         * 输出"✅ 唯一命中"提示，并抑制通用口径重排和分数消歧。
         */
        boolean triggered() {
            return family != null && !family.isEmpty();
        }
    }

    // ==================== 消歧追问机制 ====================

    /**
     * 消歧判断：当检索结果存在多义性时，在返回中附加消歧指令。
     * <p>
     * 触发条件：
     * <ul>
     *   <li>指标多义：≥2 个指标且前两名分数差距 < DISAMBIGUATION_SCORE_GAP</li>
     *   <li>维度多义：≥2 个同类维度（如 region/province/city）且前两名分数差距 < DISAMBIGUATION_SCORE_GAP</li>
     * </ul>
     * <p>
     * 消歧指令明确告诉 LLM "不要自行选择，必须让用户确认"，避免 LLM 随意挑选导致答非所问。
     */
    private void appendDisambiguationHint(StringBuilder sb,
                                           List<AloudataSearchResult.MetricHit> metrics,
                                           List<AloudataSearchResult.DimensionHit> dimensions,
                                           String keyword,
                                           boolean suppressMetric) {
        boolean hasMetricAmbiguity = false;
        boolean hasDimensionAmbiguity = false;

        // 指标消歧：≥2 个指标且前两名分数接近（族级已权威处理时跳过，避免与族级口径消歧重复）
        // 额外：同族不同口径指标（如"交易市占率（整体）"vs"（个人）"）无论分数差距如何都强制消歧，
        // 因为同族指标的语义差异是业务口径差异，与分数无关
        if (!suppressMetric && metrics.size() >= 2) {
            double topScore = metrics.get(0).getScore();
            double secondScore = metrics.get(1).getScore();
            if (topScore - secondScore < DISAMBIGUATION_SCORE_GAP) {
                hasMetricAmbiguity = true;
            }
            // 同族不同口径检测：剥离尾部口径后比较基名
            if (!hasMetricAmbiguity && areSameFamilyMetrics(metrics.get(0), metrics.get(1))) {
                hasMetricAmbiguity = true;
            }
        }

        // 维度消歧：≥2 个同类维度且前两名分数接近
        // "同类"判断：维度展示名包含相同的关键词（如"区域"/"地区"/"省份"等），或维度名属于同一分组
        if (dimensions.size() >= 2) {
            double topScore = dimensions.get(0).getScore();
            double secondScore = dimensions.get(1).getScore();
            if (topScore - secondScore < DISAMBIGUATION_SCORE_GAP) {
                hasDimensionAmbiguity = true;
            }
            // 额外检查：同类维度（如 region/province/city 都是地理维度）
            if (!hasDimensionAmbiguity && areSimilarDimensions(dimensions)) {
                hasDimensionAmbiguity = true;
            }
        }

        if (!hasMetricAmbiguity && !hasDimensionAmbiguity) {
            return;
        }

        sb.append("## ⚠️ 消歧提示\n\n");
        sb.append("检索到的结果存在多义性，**请不要自行选择，必须向用户确认后再构造查询**。\n\n");
        sb.append("**转述要求（硬性）**：必须将候选中的**全部**指标逐条、原样转述给用户选择；");
        sb.append("禁止只列前几个、禁止凭印象自行筛选、禁止凭记忆补全检索结果中不存在的指标。遗漏候选视为执行失败。\n\n");

        if (hasMetricAmbiguity) {
            sb.append("**指标消歧**：用户提到的「").append(keyword).append("」可能对应以下 ")
                    .append(metrics.size()).append(" 个候选指标，请让用户选择（请完整转述下方全部候选，不得遗漏）：\n");
            for (int i = 0; i < metrics.size(); i++) {
                AloudataSearchResult.MetricHit hit = metrics.get(i);
                sb.append("  ").append(i + 1).append(". **").append(hit.getMetricName()).append("**(")
                        .append(hit.getMetricDisplayName() != null ? hit.getMetricDisplayName() : "").append(")");
                if (hit.getBusinessCaliber() != null && !hit.getBusinessCaliber().isBlank()) {
                    sb.append(" — ").append(hit.getBusinessCaliber());
                }
                sb.append("\n");
            }
            if (metrics.size() >= 2) {
                sb.append("追问示例：\"「").append(keyword).append("」可能对应 ")
                        .append(metrics.get(0).getMetricDisplayName() != null ? metrics.get(0).getMetricDisplayName() : metrics.get(0).getMetricName())
                        .append(" 或 ")
                        .append(metrics.get(1).getMetricDisplayName() != null ? metrics.get(1).getMetricDisplayName() : metrics.get(1).getMetricName())
                        .append("，请选择具体指标\"\n\n");
            }
        }

        if (hasDimensionAmbiguity) {
            sb.append("**维度消歧**：检索到多个相似维度，请让用户确认具体维度：\n");
            for (int i = 0; i < dimensions.size(); i++) {
                AloudataSearchResult.DimensionHit hit = dimensions.get(i);
                sb.append("  ").append(i + 1).append(". **").append(hit.getDimName()).append("**(")
                        .append(hit.getDimDisplayName() != null ? hit.getDimDisplayName() : "").append(")");
                if (hit.getDimDescription() != null && !hit.getDimDescription().isBlank()) {
                    sb.append(" — ").append(hit.getDimDescription());
                }
                sb.append("\n");
            }
            if (dimensions.size() >= 2) {
                sb.append("追问示例：\"可按 ")
                        .append(dimensions.get(0).getDimDisplayName() != null ? dimensions.get(0).getDimDisplayName() : dimensions.get(0).getDimName())
                        .append(" 或 ")
                        .append(dimensions.get(1).getDimDisplayName() != null ? dimensions.get(1).getDimDisplayName() : dimensions.get(1).getDimName())
                        .append(" 查看数据，请选择具体维度\"\n\n");
            }
        }
    }

    /**
     * 判断两个指标是否属于同一指标族（相同基名、不同口径）。
     * <p>
     * 剥离展示名尾部的口径后缀后比较基名：基名相同则视为同族。
     * 例如"交易市占率（整体）"和"交易市占率（个人）"剥离后基名均为"交易市占率"→同族。
     * <p>
     * 用于消歧判断：同族不同口径指标无论分数差距如何都应消歧，
     * 因为口径差异是业务语义差异，不是检索排序能解决的。
     */
    private boolean areSameFamilyMetrics(AloudataSearchResult.MetricHit m1, AloudataSearchResult.MetricHit m2) {
        if (m1 == null || m2 == null) {
            return false;
        }
        String name1 = m1.getMetricDisplayName();
        String name2 = m2.getMetricDisplayName();
        if (name1 == null || name2 == null || name1.isBlank() || name2.isBlank()) {
            return false;
        }
        String base1 = stripTrailingCaliber(name1);
        String base2 = stripTrailingCaliber(name2);
        // 基名相同且至少有一个原始展示名不同于基名（即至少有一个带口径后缀）
        return base1.equals(base2) && (!base1.equals(name1) || !base2.equals(name2));
    }

    /**
     * 判断维度列表中是否存在同类维度（地理层级、时间层级等）
     * <p>
     * 同类维度的特征：维度名属于同一分组（如 region/province/city 都是地理层级）。
     * 检测方式：维度展示名中包含共同的关键词，或维度名属于预定义的同类分组。
     */
    private boolean areSimilarDimensions(List<AloudataSearchResult.DimensionHit> dimensions) {
        if (dimensions.size() < 2) {
            return false;
        }

        // 预定义的同类维度分组
        List<Set<String>> dimensionGroups = List.of(
                Set.of("region", "province", "city", "district", "area", "country"),
                Set.of("channel", "channel_type", "channel_group", "sales_channel"),
                Set.of("product", "product_category", "product_type", "product_line", "product_brand"),
                Set.of("customer", "customer_type", "customer_group", "customer_level"),
                Set.of("department", "org", "organization", "team", "business_unit")
        );

        // 收集所有维度名
        Set<String> dimNames = dimensions.stream()
                .map(AloudataSearchResult.DimensionHit::getDimName)
                .collect(Collectors.toSet());

        // 检查是否有 ≥2 个维度名属于同一分组
        for (Set<String> group : dimensionGroups) {
            long matchCount = dimNames.stream().filter(group::contains).count();
            if (matchCount >= 2) {
                return true;
            }
        }

        // 补充检查：展示名中包含共同关键词（如"区域"/"地区"/"省份"）
        Set<String> commonKeywords = Set.of("区域", "地区", "省份", "城市", "渠道", "产品", "客户", "部门", "组织");
        long keywordMatchCount = 0;
        for (AloudataSearchResult.DimensionHit hit : dimensions) {
            if (hit.getDimDisplayName() != null) {
                for (String kw : commonKeywords) {
                    if (hit.getDimDisplayName().contains(kw)) {
                        keywordMatchCount++;
                        break;
                    }
                }
            }
        }
        return keywordMatchCount >= 2;
    }

    // ==================== 答案数字对齐证据 + 查询基座持久化（P0-1 / P0-2） ====================

    /**
     * P0-1/P0-2: 指标查询【成功】后执行的两件事：
     * <ol>
     *   <li>从 RAW 响应（spill/截断前）抽取数值证据写入会话 scope，供 {@code MetricAnswerVerifier}
     *       在收尾时对最终答案做数字对齐校验；</li>
     *   <li>把成功的结构化查询参数（metrics/dimensions/timeConstraint/filters/指标映射）持久化为
     *       会话级「查询基座」（{@code dataagent_query_state}），供下一轮追问直接复用，避免依赖
     *       会被压缩的会话历史。</li>
     * </ol>
     * 两件事都尽力而为（失败仅告警），不影响主链路。
     */
    @SuppressWarnings("unchecked")
    private void recordMetricQueryEvidence(Long datasourceId, Map<String, Object> params,
                                           ResponseEntity<Map> response) {
        try {
            String conversationId = ToolExecutionContext.conversationId();

            Map<String, Object> body = response != null ? response.getBody() : null;
            boolean success = body != null && Boolean.TRUE.equals(body.get("success"));
            if (success) {
                MetricQueryEvidence evidence = buildEvidence(datasourceId, params, body);
                if (conversationId != null && !conversationId.isBlank()) {
                    scopeContext.addMetricQueryEvidence(conversationId, evidence);
                    // P0-2: 持久化成功查询基座
                    saveQueryState(conversationId, datasourceId, params);
                }
            }
        } catch (Exception e) {
            log.warn("指标查询证据记录/基座持久化失败，跳过: {}", e.getMessage());
        }
    }

    /**
     * 从 RAW 指标查询响应构建数字证据快照。
     */
    @SuppressWarnings("unchecked")
    private MetricQueryEvidence buildEvidence(Long datasourceId, Map<String, Object> params,
                                              Map<String, Object> body) {
        MetricQueryEvidence.Builder builder = MetricQueryEvidence.builder(datasourceId);

        List<String> metricNames = extractStringList(params, "metrics", new ArrayList<>());
        for (String m : metricNames) {
            builder.addMetricName(m.contains("__") ? m.split("__")[0] : m);
        }
        List<String> dims = extractStringList(params, "dimensions", new ArrayList<>());
        for (String d : dims) {
            builder.addDimensionName(d);
        }

        int rowCount = 0;
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            if (data.get("table") instanceof Map<?, ?> table) {
                if (table.get("columns") instanceof Map<?, ?> columns) {
                    for (Object listObj : columns.values()) {
                        if (listObj instanceof List<?> col) {
                            if (col.size() > rowCount) {
                                rowCount = col.size();
                            }
                            for (Object cell : col) {
                                if (cell instanceof Map<?, ?> cv) {
                                    Object v = cv.get("value");
                                    builder.addNumber(v != null ? v.toString() : null);
                                } else if (cell != null) {
                                    builder.addNumber(cell.toString());
                                }
                            }
                        }
                    }
                }
                if (table.get("rows") instanceof List<?> rows) {
                    if (rows.size() > rowCount) {
                        rowCount = rows.size();
                    }
                    for (Object rowObj : rows) {
                        if (rowObj instanceof Map<?, ?> row) {
                            for (Object v : row.values()) {
                                if (v != null) {
                                    builder.addNumber(v.toString());
                                }
                            }
                        }
                    }
                }
            }
            Object totalObj = data.get("total");
            if (totalObj instanceof Number) {
                builder.addNumber(totalObj.toString());
            }
        }
        return builder.withRowCount(rowCount).build();
    }

    /**
     * P0-2: 把成功查询的结构化参数持久化为会话级查询基座。
     */
    private void saveQueryState(String conversationId, Long datasourceId, Map<String, Object> params) {
        try {
            QueryStateEntity state = new QueryStateEntity();
            state.setConversationId(conversationId);
            state.setDatasourceId(datasourceId);
            state.setMetrics(toJsonOrNull(params.get("metrics")));
            state.setDimensions(toJsonOrNull(params.get("dimensions")));
            Object tc = params.get("timeConstraint");
            state.setTimeConstraint(tc != null ? tc.toString() : null);
            state.setFilters(toJsonOrNull(params.get("filters")));
            state.setOrders(toJsonOrNull(params.get("orders")));
            state.setMetricDisplayMap(buildMetricDisplayMap(datasourceId, params));
            state.setRequestJson(toJsonOrNull(params));
            queryStateService.upsert(state);
        } catch (Exception e) {
            log.debug("查询基座持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 构建指标英文名 → 中文展示名/口径 映射（供下一轮追问提示展示"已验证指标"）。
     */
    private String buildMetricDisplayMap(Long datasourceId, Map<String, Object> params) {
        List<String> metrics = extractStringList(params, "metrics", new ArrayList<>());
        if (metrics.isEmpty()) {
            return null;
        }
        Set<String> baseNames = new LinkedHashSet<>();
        for (String m : metrics) {
            String base = m.contains("__") ? m.split("__")[0] : m;
            if (!base.isBlank()) {
                baseNames.add(base);
            }
        }
        if (baseNames.isEmpty()) {
            return null;
        }
        try {
            List<AloudataMetricEntity> entities = metricMapper.selectList(
                    new LambdaQueryWrapper<AloudataMetricEntity>()
                            .eq(AloudataMetricEntity::getDatasourceId, datasourceId)
                            .in(AloudataMetricEntity::getMetricName, baseNames)
                            .select(AloudataMetricEntity::getMetricName,
                                    AloudataMetricEntity::getMetricDisplayName,
                                    AloudataMetricEntity::getBusinessCaliber));
            JSONObject map = new JSONObject(new LinkedHashMap<>());
            for (AloudataMetricEntity e : entities) {
                if (e.getMetricName() == null) {
                    continue;
                }
                JSONObject info = new JSONObject(new LinkedHashMap<>());
                info.set("displayName", e.getMetricDisplayName());
                info.set("caliber", e.getBusinessCaliber());
                map.set(e.getMetricName(), info);
            }
            return map.isEmpty() ? null : map.toString();
        } catch (Exception e) {
            log.debug("构建指标映射失败: {}", e.getMessage());
            return null;
        }
    }

    /** 对象转 JSON 字符串；null 返回 null（避免 JSONUtil 对 null 输出 "null" 文本） */
    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String json = JSONUtil.toJsonStr(value);
        return "null".equals(json) ? null : json;
    }
}
