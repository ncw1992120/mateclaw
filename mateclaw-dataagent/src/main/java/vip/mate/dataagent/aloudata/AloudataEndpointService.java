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
        Map<String, ApiEndpoint> defaults = new LinkedHashMap<>();

        // ==================== 类目管理 ====================
        defaults.put("category_list", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/category/list", "GET", "查询指标类目列表",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("type", "String", false, null, "类目类型：METRIC/DIMENSION", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Array[Object]", true, null, "类目列表", null),
                        new ApiParam("data[].id", "String", true, null, "类目ID", null),
                        new ApiParam("data[].name", "String", true, null, "类目名称", null),
                        new ApiParam("data[].parentId", "String", false, null, "父类目ID", null),
                        new ApiParam("data[].type", "String", true, null, "类目类型：METRIC/DIMENSION", null)
                )
        ));

        // ==================== 指标管理 ====================
        defaults.put("metrics_list", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/metrics/list", "GET", "获取某个租户下所有的指标列表",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("keyword", "String", false, null, "搜索关键词", "QUERY"),
                        new ApiParam("metricCategoryId", "String", false, null, "指标所属类目ID，-1表示未分类", "QUERY"),
                        new ApiParam("statusFilters", "Array", false, null,
                                "指标状态过滤，枚举：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE", "QUERY"),
                        new ApiParam("pageNumber", "Integer", false, "1", "当前页码", "QUERY"),
                        new ApiParam("pageSize", "Integer", false, "20", "每页大小", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "响应结果（含total/pageNumber/pageSize/hasNext/data列表）", null),
                        new ApiParam("data.data[].code", "String", false, null, "指标编码（系统内部生成）", null),
                        new ApiParam("data.data[].metricName", "String", true, null, "指标名称（英文名）", null),
                        new ApiParam("data.data[].metricDisplayName", "String", true, null, "指标展示名", null),
                        new ApiParam("data.data[].type", "String", true, null, "指标类型：ATOMIC/derived/composite", null),
                        new ApiParam("data.data[].businessCaliber", "String", false, null, "指标描述信息", null),
                        new ApiParam("data.data[].owner", "String", true, null, "指标负责人", null),
                        new ApiParam("data.data[].businessOwner", "String", true, null, "业务负责人", null),
                        new ApiParam("data.data[].metricCategoryId", "String", true, null, "指标类目ID", null),
                        new ApiParam("data.data[].status", "String", true, null, "指标终态：ONLINE/OFFLINE", null),
                        new ApiParam("data.data[].publishStatus", "String", true, null, "发布状态：DRAFT/PUBLISHED", null),
                        new ApiParam("data.data[].unit", "String", false, null, "指标单位枚举值", null)
                )
        ));

        defaults.put("metric_detail", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/metrics/detail", "GET", "查询单个指标详情",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricName", "String", true, null, "指标名称", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "指标详情对象", null)
                )
        ));

        defaults.put("metric_batch_detail", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/metrics/batch-detail", "POST", "批量查询指标详情",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricNames", "Array[String]", true, null, "指标名称集合", "BODY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Array[Object]", true, null, "指标详情列表", null)
                )
        ));

        defaults.put("metric_tree", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/metrics/tree", "GET", "获取树状结构的指标列表",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("keyword", "String", false, null, "搜索关键词", "QUERY"),
                        new ApiParam("metricCategoryId", "String", false, null, "指标所属类目ID", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Array[Object]", true, null, "树状结构指标列表", null)
                )
        ));

        defaults.put("metric_available_dimensions", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/metrics/dimension", "GET", "查询指标可用维度（支持多指标交集）",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricNames", "Array[String]", true, null,
                                "指标名称集合，单个指标返回所有可用维度，多个指标返回交集", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Array[Object]", true, null, "可用维度列表", null),
                        new ApiParam("data[].dimName", "String", true, null, "维度英文名", null),
                        new ApiParam("data[].dimDisplayName", "String", true, null, "维度展示名", null),
                        new ApiParam("data[].originDataType", "String", true, null, "维度数据类型", null),
                        new ApiParam("data[].datasetName", "String", false, null, "维度绑定的数据集名称", null),
                        new ApiParam("data[].config.type", "String", true, null, "维度类型：COLUMN_BIND/CUSTOM", null),
                        new ApiParam("data[].config.value", "String", true, null, "列名或自定义表达式", null)
                )
        ));

        // ==================== 指标查询 ====================
        defaults.put("metrics_query", new ApiEndpoint("semantic",
                "/semantic/api/v1.1/metrics/query", "POST", "使用指标和维度组合，查询指定的指标计算结果",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metrics", "Array[String]", true, null,
                                "查询指标列表，支持直接引用、快速计算（同环比/占比/排名/时间限定）", "BODY"),
                        new ApiParam("metricDefinitions", "Map", false, null,
                                "临时指标定义，key为临时指标名，value包含refMetric和specifyDimension", "BODY"),
                        new ApiParam("dimensions", "Array[String]", false, null,
                                "查询维度列表，支持日期粒度切换（如metric_time__day）", "BODY"),
                        new ApiParam("filters", "Array[String]", false, null,
                                "全局筛选，对全部指标进行维度数据过滤", "BODY"),
                        new ApiParam("resultFilters", "Array[String]", false, null,
                                "结果筛选，对查询结果进行筛选", "BODY"),
                        new ApiParam("timeConstraint", "String", false, null,
                                "指标日期范围，如 ([metric_time__month]= DateTrunc(Today(),\"MONTH\"))", "BODY"),
                        new ApiParam("orders", "Map", false, null,
                                "排序，内容需包含在metrics或dimensions中", "BODY"),
                        new ApiParam("limit", "Integer", false, "100", "返回结果条数", "BODY"),
                        new ApiParam("offset", "Integer", false, "1", "返回结果偏移量", "BODY"),
                        new ApiParam("queryResultType", "String", false, "SQL_AND_DATA",
                                "返回数据类型：SQL_AND_DATA/SQL/DATA", "BODY"),
                        new ApiParam("source", "String", false, null, "数据查询来源标识（自定义）", "BODY"),
                        new ApiParam("isQueryTotalCount", "Boolean", false, "false", "是否返回数据总条数", "BODY"),
                        new ApiParam("specialMvConfig", "Map", false, null,
                                "物化加速配置，控制是否启用指定物化表加速", "BODY")
                )),
                List.of(
                        new ApiParam("success", "Boolean", true, null, "查询是否成功", null),
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("message", "String", false, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data.queryId", "String", true, null, "查询ID", null),
                        new ApiParam("data.sql", "String", false, null, "查询SQL（queryResultType含SQL时返回）", null),
                        new ApiParam("data.warning", "String", false, null, "查询警告信息", null),
                        new ApiParam("data.table", "Object", true, null, "查询结果数据，key为列名，value为值数组", null),
                        new ApiParam("data.metas", "Array[Object]", true, null, "列元数据信息", null),
                        new ApiParam("data.metas[].name", "String", true, null, "字段名称", null),
                        new ApiParam("data.metas[].dataTypeName", "String", true, null, "字段类型名称（如DATETIME/BIGINT/DECIMAL）", null)
                )
        ));

        // ==================== 维度管理 ====================
        defaults.put("dimensions_list", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/dimensions/list", "GET", "获取指定条件下的维度列表",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("keyword", "String", false, null, "搜索关键词", "QUERY"),
                        new ApiParam("dimCategoryId", "String", false, null, "维度所属类目ID", "QUERY"),
                        new ApiParam("statusFilters", "Array", false, null, "维度状态过滤", "QUERY"),
                        new ApiParam("pageNumber", "Integer", false, "1", "当前页码", "QUERY"),
                        new ApiParam("pageSize", "Integer", false, "20", "每页大小", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "响应结果（含total/pageNumber/pageSize/data列表）", null),
                        new ApiParam("data.data[].dimName", "String", true, null, "维度英文名", null),
                        new ApiParam("data.data[].dimDisplayName", "String", true, null, "维度展示名", null),
                        new ApiParam("data.data[].originDataType", "String", true, null, "维度数据类型", null),
                        new ApiParam("data.data[].dimDescription", "String", false, null, "维度描述", null)
                )
        ));

        defaults.put("dimension_detail", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/dimensions/detail", "GET", "查询维度详情",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("dimName", "String", true, null, "维度名称", "QUERY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "维度详情对象", null)
                )
        ));

        defaults.put("dimension_values", new ApiEndpoint("anymetrics",
                "/anymetrics/api/v1/dimension/values", "POST", "预览指定维度的取值情况",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("dimName", "String", true, null, "维度名称", "BODY"),
                        new ApiParam("dimValueKeyword", "String", false, null,
                                "维度值关键词（模糊匹配，仅对字符串类型维度有效）", "BODY"),
                        new ApiParam("pageNumber", "Integer", false, "1", "页码，从1开始", "BODY"),
                        new ApiParam("pageSize", "Integer", false, "200", "每页记录条数", "BODY")
                )),
                List.of(
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("data.queryId", "String", true, null, "查询ID", null),
                        new ApiParam("data.metas", "Array[Object]", true, null, "列元数据", null),
                        new ApiParam("data.metas[].name", "String", true, null, "字段名称", null),
                        new ApiParam("data.metas[].dataTypeName", "String", true, null, "字段类型名称", null),
                        new ApiParam("data.table", "Object", true, null, "查询结果数据，key为列名", null)
                )
        ));

        // ==================== 归因分析 ====================
        defaults.put("attribution_tree", new ApiEndpoint("semantic",
                "/semantic/api/v1/attribution/tree", "POST", "针对指标以树解耦进行归因分析报告查询",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricName", "String", true, null, "指标名称", "BODY"),
                        new ApiParam("dimensions", "Array[String]", false, null, "分析维度列表", "BODY"),
                        new ApiParam("timeConstraint", "String", false, null, "时间范围约束", "BODY"),
                        new ApiParam("filters", "Array[String]", false, null, "全局筛选条件", "BODY")
                )),
                List.of(
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "归因分析树结果", null)
                )
        ));

        defaults.put("attribution_multi_dim", new ApiEndpoint("semantic",
                "/semantic/api/v1/attribution/multi-dim", "POST", "针对指标及维度进行多维归因结果查询",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricName", "String", true, null, "指标名称", "BODY"),
                        new ApiParam("dimensions", "Array[String]", true, null, "分析维度列表", "BODY"),
                        new ApiParam("timeConstraint", "String", false, null, "时间范围约束", "BODY"),
                        new ApiParam("filters", "Array[String]", false, null, "全局筛选条件", "BODY")
                )),
                List.of(
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "多维归因分析结果", null)
                )
        ));

        defaults.put("attribution_validate", new ApiEndpoint("semantic",
                "/semantic/api/v1/attribution/validate", "POST", "校验指标是否能够进行归因分析",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricName", "String", true, null, "指标名称", "BODY"),
                        new ApiParam("dimensions", "Array[String]", false, null, "分析维度列表", "BODY")
                )),
                List.of(
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "校验结果", null)
                )
        ));

        defaults.put("attribution_drilldown", new ApiEndpoint("semantic",
                "/semantic/api/v1/attribution/drilldown", "POST", "针对指标及维度进行多维归因下钻查询",
                mergeParams(commonHeaderParams(), List.of(
                        new ApiParam("metricName", "String", true, null, "指标名称", "BODY"),
                        new ApiParam("dimensions", "Array[String]", true, null, "下钻维度列表", "BODY"),
                        new ApiParam("timeConstraint", "String", false, null, "时间范围约束", "BODY"),
                        new ApiParam("filters", "Array[String]", false, null, "全局筛选条件", "BODY")
                )),
                List.of(
                        new ApiParam("success", "Boolean", true, null, "请求是否成功", null),
                        new ApiParam("code", "String", true, null, "接口响应码", null),
                        new ApiParam("errorMsg", "String", true, null, "报错信息", null),
                        new ApiParam("traceId", "String", true, null, "追踪ID", null),
                        new ApiParam("data", "Object", true, null, "下钻查询结果", null)
                )
        ));

        return defaults;
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