package vip.mate.dataagent.dto;

import java.util.List;

/** Python 最终结果集上的白名单查询配置，不描述数据源查询。 */
public record FinalResultQueryConfigDTO(
        String schemaFingerprint,
        List<DisplayField> displayFields,
        List<FilterField> filterFields,
        List<ParameterBinding> parameterBindings,
        SortPolicy sortPolicy,
        PaginationPolicy paginationPolicy) {

    public FinalResultQueryConfigDTO {
        displayFields = displayFields == null ? List.of() : List.copyOf(displayFields);
        filterFields = filterFields == null ? List.of() : List.copyOf(filterFields);
        parameterBindings = parameterBindings == null ? List.of() : List.copyOf(parameterBindings);
        sortPolicy = sortPolicy == null ? new SortPolicy(false, List.of()) : sortPolicy;
        paginationPolicy = paginationPolicy == null ? new PaginationPolicy(false, 100, 500, true) : paginationPolicy;
    }

    public record DisplayField(String field, String title, String role, String dataType) {}

    public record FilterField(String field, String title, String dataType,
                              String parameterName, List<String> operators) {
        public FilterField {
            operators = operators == null ? List.of() : List.copyOf(operators);
        }
    }

    public record ParameterBinding(String parameterName, String field, String operator) {}

    public record SortPolicy(boolean enabled, List<String> allowedFields) {
        public SortPolicy {
            allowedFields = allowedFields == null ? List.of() : List.copyOf(allowedFields);
        }
    }

    public record PaginationPolicy(boolean enabled, int defaultPageSize, int maxPageSize,
                                   boolean returnTotalCount) {
        public PaginationPolicy {
            if (defaultPageSize < 1 || maxPageSize < 1 || defaultPageSize > maxPageSize) {
                throw new IllegalArgumentException("invalid final result pagination policy");
            }
        }
    }
}
