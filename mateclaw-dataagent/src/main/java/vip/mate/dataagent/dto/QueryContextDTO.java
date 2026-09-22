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
        SortSpec sort,
        PaginationSpec pagination,
        String requestId) {

    /** 服务端首轮每页条数硬上限。 */
    public static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    public QueryContextDTO {
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
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
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
