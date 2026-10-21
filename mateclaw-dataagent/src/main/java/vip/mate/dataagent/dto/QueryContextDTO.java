package vip.mate.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * 页面运行时查询上下文（设计文档第七节同形 + requestId 链路关联标识）。
 * <p>
 * 契约（实施计划「统一接口契约 §1」）：
 * <ul>
 *   <li>{@code datasetId} 仅用于数据卡片「查看数据」；组件执行时可省略。</li>
 *   <li>{@code parameters} 的键必须来自已绑定筛选器的参数定义，客户端不得自造 field/operator。</li>
 *   <li>{@code pagination.page} 从 1 开始；{@code pageSize} 服务端硬上限 500（maxPageSize 在 Planner 结合配置进一步收窄）。</li>
 *   <li>{@code requestId} 是链路关联标识，不属于持久化配置。</li>
 * </ul>
 * 不可变 DTO；校验失败抛 {@link IllegalArgumentException}，由入口映射为 QUERY_CONTEXT_INVALID。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryContextDTO(
        String dashboardId,
        String componentId,
        String datasetId,
        Map<String, Object> parameters,
        Map<String, Map<String, Object>> datasetParameters,
        SortSpec sort,
        PaginationSpec pagination,
        String requestId,
        Map<String, InputQueryOptions> datasetQueries) {

    public QueryContextDTO(String dashboardId, String componentId, String datasetId,
                           Map<String, Object> parameters, Map<String, Map<String, Object>> datasetParameters,
                           SortSpec sort, PaginationSpec pagination, String requestId) {
        this(dashboardId, componentId, datasetId, parameters, datasetParameters, sort, pagination, requestId, Map.of());
    }

    /** 输入查询选项与 Python 输出分页分离；仅编辑态允许重放上游查询快照。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InputQueryOptions(boolean editor, String timeGranularity, SortSpec sort,
                                    PaginationSpec pagination, Integer queryLimit,
                                    java.util.List<DatasetQueryPlanDTO.FilterSpec> filters,
                                    Map<String, Object> parameters, java.util.List<String> timeFields) {
        public InputQueryOptions {
            if (timeGranularity != null && !java.util.Set.of("YEAR", "QUARTER", "MONTH", "WEEK", "DAY").contains(timeGranularity)) {
                throw new IllegalArgumentException("unsupported input time granularity");
            }
            if (queryLimit != null && (queryLimit < 1 || queryLimit > 100_000)) {
                throw new IllegalArgumentException("input queryLimit must be between 1 and 100000");
            }
            filters = filters == null ? null : java.util.List.copyOf(filters);
            timeFields = timeFields == null ? java.util.List.of() : java.util.List.copyOf(timeFields);
            parameters = parameters == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters));
            if (!editor && (filters != null || !parameters.isEmpty() || sort != null || pagination != null || queryLimit != null)) {
                throw new IllegalArgumentException("input query snapshots are only supported in editor mode");
            }
        }
    }

    /** 向后兼容旧请求与现有 Java 调用点。 */
    public QueryContextDTO(String dashboardId, String componentId, String datasetId,
                           Map<String, Object> parameters, SortSpec sort,
                           PaginationSpec pagination, String requestId) {
        this(dashboardId, componentId, datasetId, parameters, Map.of(), sort, pagination, requestId);
    }

    /** 服务端首轮每页条数硬上限。 */
    public static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    public QueryContextDTO {
        datasetQueries = datasetQueries == null ? Map.of() : Map.copyOf(datasetQueries);
        if (dashboardId == null || dashboardId.isBlank()) {
            throw new IllegalArgumentException("dashboardId is required");
        }
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId is required");
        }
        if (datasetId != null && datasetId.isBlank()) {
            datasetId = null;
        }
        // 注意：参数值允许 null（null = 清空筛选），因此不能用 Map.copyOf
        parameters = parameters == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters));
        Map<String, Map<String, Object>> scoped = new java.util.LinkedHashMap<>();
        if (datasetParameters != null) {
            datasetParameters.forEach((inputName, values) -> {
                if (inputName == null || inputName.isBlank()) {
                    throw new IllegalArgumentException("dataset parameter input name must not be blank");
                }
                scoped.put(inputName, values == null ? Map.of()
                        : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values)));
            });
        }
        datasetParameters = java.util.Collections.unmodifiableMap(scoped);
        Map<String, Object> allParameters = new java.util.LinkedHashMap<>(parameters);
        datasetParameters.values().forEach(allParameters::putAll);
        for (Map.Entry<String, Object> entry : allParameters.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("parameter name must not be blank");
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof CharSequence || value instanceof Number
                    || value instanceof Boolean || value instanceof Collection<?>)) {
                throw new IllegalArgumentException("unsupported parameter value type: " + entry.getKey());
            }
        }
        if (requestId != null && requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw new IllegalArgumentException("requestId exceeds " + MAX_REQUEST_ID_LENGTH + " characters");
        }
    }

    /** 组件级参数保留兼容；当前输入的作用域参数覆盖同名全局参数。 */
    public Map<String, Object> parametersFor(String inputName) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(parameters);
        result.putAll(datasetParameters.getOrDefault(inputName, Map.of()));
        return java.util.Collections.unmodifiableMap(result);
    }

    private Map<String, Object> allParameters() {
        Map<String, Object> result = new java.util.LinkedHashMap<>(parameters);
        datasetParameters.values().forEach(result::putAll);
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SortSpec(String field, String direction) {
        public SortSpec {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("sort field is required");
            }
            if (direction == null || direction.isBlank()) {
                direction = "asc";
            }
            direction = direction.toLowerCase(Locale.ROOT);
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw new IllegalArgumentException("sort direction must be asc or desc");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaginationSpec(int page, int pageSize) {
        public PaginationSpec {
            if (page < 1) {
                throw new IllegalArgumentException("pagination.page starts from 1");
            }
            if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException("pagination.pageSize must be between 1 and " + MAX_PAGE_SIZE);
            }
        }

        public int offset() {
            return (page - 1) * pageSize;
        }
    }
}
