package vip.mate.dataagent.dataset;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Map;

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
            case "eq", "neq", "gt", "gte", "lt", "lte", "in", "not_in", "between", "contains",
                 "is_null", "is_not_null" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException("unsupported filter operator: " + operator);
        }
        if (value == null && !normalizedOperator.equals("is_null") && !normalizedOperator.equals("is_not_null")) {
            throw new IllegalArgumentException("filter value must not be null for operator: " + operator);
        }
    }

    private static final Map<String, String> ROLE_ALIASES = Map.of(
            "dim", "dimension", "dimension", "dimension",
            "metric", "measure", "measure", "measure");

    private static final Map<String, String> OPERATOR_ALIASES = Map.ofEntries(
            Map.entry("=", "eq"), Map.entry("==", "eq"), Map.entry("eq", "eq"), Map.entry("equal", "eq"),
            Map.entry("!=", "neq"), Map.entry("<>", "neq"), Map.entry("neq", "neq"),
            Map.entry(">", "gt"), Map.entry("gt", "gt"),
            Map.entry(">=", "gte"), Map.entry("gte", "gte"),
            Map.entry("<", "lt"), Map.entry("lt", "lt"),
            Map.entry("<=", "lte"), Map.entry("lte", "lte"),
            Map.entry("in", "in"),
            Map.entry("not_in", "not_in"), Map.entry("not in", "not_in"), Map.entry("nin", "not_in"),
            Map.entry("between", "between"),
            // contains 是「包含子串」，编译成 LIKE '%value%'；不用 like 做别名 —— 那个语义是用户自带通配符，容易混
            Map.entry("contains", "contains"), Map.entry("has", "contains"),
            Map.entry("is_null", "is_null"), Map.entry("is null", "is_null"), Map.entry("null", "is_null"),
            Map.entry("is_not_null", "is_not_null"), Map.entry("is not null", "is_not_null"),
            Map.entry("not_null", "is_not_null"));

    /**
     * JSON 反序列化入口：容忍 {@code op} 别名、符号运算符与缺省 role。
     * 内部调用方请直接使用构造器，保持下推条件显式可审计。
     */
    @JsonCreator
    static DatasetFilter fromJson(@JsonProperty("field") String field,
                                  @JsonProperty("role") String role,
                                  @JsonProperty("operator") @JsonAlias({"op", "operatorType"}) String operator,
                                  @JsonProperty("value") Object value) {
        return new DatasetFilter(field, normalizeRole(role), normalizeOperator(operator), value);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "dimension";
        String normalized = role.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return ROLE_ALIASES.getOrDefault(normalized, role);
    }

    private static String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("filter operator is required（可用: eq(=), neq(!=), gt(>), gte(>=), lt(<), lte(<=), in, not_in, between, is_null, is_not_null）");
        }
        String normalized = operator.trim().toLowerCase(Locale.ROOT);
        String mapped = OPERATOR_ALIASES.get(normalized);
        if (mapped == null) mapped = OPERATOR_ALIASES.get(normalized.replace(' ', '_'));
        if (mapped != null) return mapped;
        throw new IllegalArgumentException(
                "unsupported filter operator: " + operator + "（可用: eq(=), neq(!=), gt(>), gte(>=), lt(<), lte(<=), in, not_in, between, is_null, is_not_null）");
    }
}
