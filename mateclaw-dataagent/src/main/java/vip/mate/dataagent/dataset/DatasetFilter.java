package vip.mate.dataagent.dataset;

import java.util.Locale;

/** 由 Python SDK 显式表达、可审计的过滤条件。 */
public record DatasetFilter(String field, String role, String operator, Object value) {
    public DatasetFilter {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("filter field must not be blank");
        }
        String normalizedRole = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (!normalizedRole.equals("dimension") && !normalizedRole.equals("measure")) {
            throw new IllegalArgumentException("unsupported filter role: " + role);
        }
        String normalizedOperator = operator == null ? "" : operator.toLowerCase(Locale.ROOT);
        if (!switch (normalizedOperator) {
            case "eq", "neq", "gt", "gte", "lt", "lte", "in", "not_in", "between", "is_null", "is_not_null" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("unsupported filter operator: " + operator);
        }
        if (value == null && !normalizedOperator.equals("is_null") && !normalizedOperator.equals("is_not_null")) {
            throw new IllegalArgumentException("filter value must not be null for operator: " + operator);
        }
    }
}
