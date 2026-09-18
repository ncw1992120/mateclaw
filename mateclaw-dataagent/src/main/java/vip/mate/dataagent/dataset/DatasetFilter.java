package vip.mate.dataagent.dataset;

import java.util.Locale;

/**
 * 由 Python SDK 显式表达、可审计的过滤条件。
 * <p>
 * 构造器保持严格（role/operator 必须是约定枚举），SDK 与内部调用方必须显式表达下推条件；
 * JSON 入口额外提供 {@link #fromJson} 宽容解析，兼容人工/前端随手写的
 * {@code op} 与 {@code = != > >= < <=} 等符号运算符，避免 400 报文只有一句 Jackson 报错。
 * <p>
 * {@code field} 是<b>数据源字段名</b>（技术主键，见 docs/策略解读/字段名与展示名契约-实施计划.md §4.3）：
 * 用户在前端改的展示名只是表现层标签，前端提交前已归一为字段名，下推链路不得出现展示名。
 */
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
