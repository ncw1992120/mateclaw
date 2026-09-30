package vip.mate.dataagent.aloudata;

import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.*;

/**
 * Aloudata 语义层筛选表达式生成（唯一出口）。
 * <p>
 * 真实 {@code semantic/api/v1.1/metrics/query} 只接受**表达式字符串数组**，形如
 * {@code ["[region] = \"华东\""]}；传结构化对象返回 {@code SM99002 系统异常}。
 * 语法支持 {@code = <> > >= < <= IN(...) NotIn(...)} 与 {@code AND/OR/()}。
 * <p>
 * <b>指标日期 {@link #METRIC_TIME_FIELD} 是分区字段，有专用写法</b>（demo 环境实测结论）：
 * <ul>
 *   <li>字段引用必须用<b>单引号</b> {@code ['metric_time']} —— 无引号或双引号在 filters 中
 *       一律被拒（HTTP 200 + 业务码 SM_02_0006/SM_02_0014，无错误详情）；</li>
 *   <li>比较条件按官方产品生成的形态写：{@code (DateTrunc(['metric_time'], "DAY") >=
 *       (DateTrunc(Cast("2026-08-26 00:00:00", "TIMESTAMP"), "DAY")))}，裸日期值需补
 *       {@code 00:00:00} 再 Cast 成 TIMESTAMP，对齐 DAY 分区裁剪；</li>
 *   <li>between 合并为单条 {@code (A AND B)}（同为实测通过的形态）。</li>
 * </ul>
 * 所有上游筛选（数据集草稿预览、指标数据集 Adapter、指标视图编译器）都应经由本类生成，
 * 避免各处各写一种写法（历史上曾出现 {@code [f] EQ ("v")}、双引号字段、结构化对象三种非法形态）。
 */
public final class AloudataFilterExpressions {

    private static final Set<String> TIME_GRANULARITIES = Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR");

    private AloudataFilterExpressions() {
    }

    /** 指标日期字段名：Aloudata 的分区字段，引用时必须单引号并按 DateTrunc/Cast 形态生成。 */
    public static final String METRIC_TIME_FIELD = "metric_time";

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

    /** 由字段 + 运算符 + 取值生成维度过滤表达式；运算符不支持或取值不合法时抛出，绝不静默丢弃条件。 */
    public static String of(String field, String operator, Object value) {
        Objects.requireNonNull(field, "filter field must not be null");
        if (METRIC_TIME_FIELD.equals(field)) {
            return metricTimeExpression(operator, value);
        }
        if (operator != null && "between".equalsIgnoreCase(operator.trim())) {
            return between(field, value);
        }
        String symbol = symbol(operator);
        if (symbol == null) {
            throw new IllegalArgumentException("不支持的筛选运算符: " + operator
                    + "（可用: eq(=), neq(!=), gt(>), gte(>=), lt(<), lte(<=), in, not_in, between）");
        }
        String ref = "[" + field + "]";
        if ("IN".equals(symbol) || "NotIn".equals(symbol)) {
            return ref + " " + symbol + " (" + String.join(",", literals(value)) + ")";
        }
        return ref + " " + symbol + " " + literal(value);
    }

    /** between 范围（闭区间）→ {@code ([field] >= 下界 AND [field] <= 上界)}。 */
    private static String between(String field, Object value) {
        List<Object> range = list(value);
        if (range.size() < 2) {
            throw new IllegalArgumentException("between 需要两个边界值: " + field);
        }
        String ref = "[" + field + "]";
        return "(" + ref + " >= " + literal(range.get(0)) + " AND " + ref + " <= " + literal(range.get(1)) + ")";
    }

    /**
     * metric_time（分区字段）专用形态，demo 环境实测通过：
     * {@code (DateTrunc(['metric_time'], "DAY") >= (DateTrunc(Cast("2026-08-26 00:00:00", "TIMESTAMP"), "DAY")))}。
     * between 展开为两条 DateTrunc 条件合并的单条 {@code (A AND B)}；IN/NotIn 不适用于分区裁剪，直接拒绝。
     */
    private static String metricTimeExpression(String operator, Object value) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("metric_time 筛选缺少运算符");
        }
        String normalized = operator.trim().toLowerCase(Locale.ROOT);
        if ("between".equals(normalized)) {
            List<Object> range = list(value);
            if (range.size() < 2) {
                throw new IllegalArgumentException("between 需要两个边界值: " + METRIC_TIME_FIELD);
            }
            return "(" + metricTimeCondition(">=", range.get(0)) + " AND " + metricTimeCondition("<=", range.get(1)) + ")";
        }
        String symbol = symbol(operator);
        if (!List.of("=", "<>", ">", ">=", "<", "<=").contains(symbol)) {
            throw new IllegalArgumentException("metric_time（分区字段）不支持运算符 " + operator
                    + "（可用: eq, neq, gt, gte, lt, lte, between）");
        }
        return "(" + metricTimeCondition(symbol, value) + ")";
    }

    /** 单侧 metric_time DateTrunc/Cast 条件（字段单引号引用，日期值补 00:00:00）。 */
    private static String metricTimeCondition(String symbol, Object value) {
        return metricTimeCondition(symbol, value, "DAY");
    }

    private static String metricTimeCondition(String symbol, Object value, String granularity) {
        return "DateTrunc(['metric_time'], \"" + granularity + "\") " + symbol
                + " (DateTrunc(Cast(" + literal(toTimestampLiteral(value)) + ", \"TIMESTAMP\"), \"" + granularity + "\"))";
    }

    /** 日期值归一为 {@code yyyy-MM-dd HH:mm:ss}：裸日期补 00:00:00，其余原样。 */
    private static String toTimestampLiteral(Object value) {
        String raw = String.valueOf(value).trim();
        return raw.length() == 10 ? raw + " 00:00:00" : raw;
    }

    public static String of(DatasetFilter filter) {
        return of(filter.field(), filter.operator(), filter.value());
    }

    /**
     * 合并 Aloudata 分区字段条件。metric_time 的范围上下界必须放在同一个 filters 字符串中，
     * 否则上游会将它们拆成独立分区表达式并返回 SM_02_0006。
     */
    public static List<String> combineMetricTimeExpressions(Collection<String> expressions) {
        List<String> ordinary = new ArrayList<>();
        List<String> metricTime = new ArrayList<>();
        if (expressions != null) {
            for (String expression : expressions) {
                if (expression == null || expression.isBlank()) continue;
                if (expression.contains("DateTrunc(['metric_time']")) metricTime.add(expression.trim());
                else ordinary.add(expression.trim());
            }
        }
        if (!metricTime.isEmpty()) {
            String combined = metricTime.size() == 1
                    ? metricTime.getFirst()
                    : String.join(" AND ", metricTime);
            ordinary.add(combined);
        }
        return List.copyOf(ordinary);
    }

    /** 生成图示中的单条 metric_time 分区范围表达式，结束值按调用方语义决定是否为排他边界。 */
    public static String metricTimeRange(Object startInclusive, Object endExclusive) {
        return metricTimeRange(startInclusive, endExclusive, "DAY");
    }

    /** 生成统一粒度的半开 metric_time 范围；缺省为 DAY，拒绝未定义粒度。 */
    public static String metricTimeRange(Object startInclusive, Object endExclusive, String granularity) {
        String normalizedGranularity = normalizeTimeGranularity(granularity);
        return "(" + metricTimeCondition(">=", startInclusive, normalizedGranularity) + ") AND ("
                + metricTimeCondition("<", endExclusive, normalizedGranularity) + ")";
    }

    /** 缺失粒度兼容 DAY；其余输入必须是精确的大写枚举值。 */
    public static String normalizeTimeGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) return "DAY";
        if (!TIME_GRANULARITIES.contains(granularity)) {
            throw new IllegalArgumentException("不支持的指标日期粒度: " + granularity
                    + "（可用: DAY, WEEK, MONTH, QUARTER, YEAR）");
        }
        return granularity;
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
