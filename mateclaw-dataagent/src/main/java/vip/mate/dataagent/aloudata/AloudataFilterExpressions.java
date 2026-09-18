package vip.mate.dataagent.aloudata;

import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.*;

/**
 * Aloudata 语义层筛选表达式生成（唯一出口）。
 * <p>
 * 真实 {@code semantic/api/v1.1/metrics/query} 只接受**表达式字符串数组**，形如
 * {@code ["[region] = \"华东\"", "[amount] >= 10"]}；传结构化对象返回 {@code SM99002 系统异常}。
 * 语法支持 {@code = <> > >= < <= IN(...) NotIn(...)} 与 {@code AND/OR/()}。
 * <p>
 * 指标视图（{@code AloudataAnalysisViewQueryCompiler}）与指标数据集
 * （{@code DatasetComposerController#previewAloudataMetrics}）等所有上游筛选都从这里生成，
 * 避免各处各写一种写法（历史上曾出现 {@code [f] EQ ("v")} 与结构化对象两种非法形态）。
 */
public final class AloudataFilterExpressions {

    private AloudataFilterExpressions() {
    }

    /** 运算符归一：接受 SDK 枚举（eq/neq/…）与符号写法（= != > …），返回 Aloudata 表达式符号。 */
    public static String symbol(String operator) {
        if (operator == null || operator.isBlank()) return null;
        return switch (operator.trim().toLowerCase(Locale.ROOT)) {
            case "eq", "=", "==" -> "=";
            case "neq", "!=", "<>" -> "<>";
            case "gt", ">" -> ">";
            case "gte", ">=" -> ">=";
            case "lt", "<" -> "<";
            case "lte", "<=" -> "<=";
            case "in" -> "IN";
            case "not_in", "not in", "nin" -> "NotIn";
            default -> null;
        };
    }

    /** 由字段 + 运算符 + 取值生成表达式；运算符不支持或取值不合法时抛出，绝不静默丢弃条件。 */
    public static String of(String field, String operator, Object value) {
        Objects.requireNonNull(field, "filter field must not be null");
        String symbol = symbol(operator);
        if (symbol == null) {
            throw new IllegalArgumentException("不支持的筛选运算符: " + operator
                    + "（可用: eq(=), neq(!=), gt(>), gte(>=), lt(<), lte(<=), in, not_in）");
        }
        String ref = "[" + field + "]";
        if ("IN".equals(symbol) || "NotIn".equals(symbol)) {
            return ref + " " + symbol + " (" + String.join(",", literals(value)) + ")";
        }
        return ref + " " + symbol + " " + literal(value);
    }

    public static String of(DatasetFilter filter) {
        if ("between".equalsIgnoreCase(String.valueOf(filter.operator()))) {
            List<Object> range = list(filter.value());
            if (range.size() < 2) {
                throw new IllegalArgumentException("between 需要两个边界值: " + filter.field());
            }
            String ref = "[" + filter.field() + "]";
            return "(" + ref + " >= " + literal(range.get(0)) + " AND " + ref + " <= " + literal(range.get(1)) + ")";
        }
        return of(filter.field(), filter.operator(), filter.value());
    }

    /** 前端/草稿入参形态（Map）→ 表达式；缺少字段或运算符时返回 null 由调用方拒绝。 */
    public static String of(Map<String, Object> filter) {
        if (filter == null) return null;
        Object field = filter.get("field");
        Object operator = filter.get("operator") != null ? filter.get("operator") : filter.get("op");
        if (field == null || operator == null) return null;
        return of(String.valueOf(field), String.valueOf(operator), filter.get("value"));
    }

    /** 标量字面量：数值/布尔原样，其余加双引号并转义。 */
    public static String literal(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static List<String> literals(Object value) {
        List<String> values = new ArrayList<>();
        for (Object item : list(value)) {
            values.add(literal(item));
        }
        return values;
    }

    /** 归一为取值列表：集合/数组原样，字符串按逗号切分（兼容 `"a,b"` 写法）。 */
    public static List<Object> list(Object value) {
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        if (value != null && value.getClass().isArray()) return new ArrayList<>(Arrays.asList((Object[]) value));
        List<Object> single = new ArrayList<>();
        if (value != null) {
            for (String part : String.valueOf(value).split(",")) {
                if (!part.isBlank()) single.add(part.trim());
            }
        }
        return single;
    }
}
