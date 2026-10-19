package vip.mate.dataagent.aloudata;

import vip.mate.dataagent.dataset.DatasetFilter;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Prevents dashboard and dataset queries from scanning Aloudata without an indicator-date condition. */
public final class MetricTimeQueryGuard {
    public static final String REQUIRED_MESSAGE = "请配置指标日期（metric_time）筛选条件后再查询";
    private static final Pattern METRIC_TIME_REFERENCE = Pattern.compile(
            "(?is)\\[\\s*'?metric_time(?:__[a-z0-9_]+)?'?\\s*]");
    private static final Pattern COMPARISON = Pattern.compile("(?s)(?:>=|<=|<>|!=|=|>|<)");

    private MetricTimeQueryGuard() {}

    public static boolean hasMetricTimeFilter(List<DatasetFilter> filters) {
        if (filters == null) return false;
        return filters.stream().anyMatch(filter -> isMetricTimeField(filter.field())
                && hasValue(filter.value())
                && !"is_null".equalsIgnoreCase(filter.operator())
                && !"is_not_null".equalsIgnoreCase(filter.operator()));
    }

    public static boolean hasMetricTimeConstraint(String timeConstraint) {
        return hasMetricTimeExpression(timeConstraint);
    }

    public static boolean hasMetricTimeFilterExpressions(List<String> expressions) {
        return expressions != null && expressions.stream().anyMatch(MetricTimeQueryGuard::hasMetricTimeExpression);
    }

    public static boolean hasMetricTimeCondition(List<DatasetFilter> filters, String timeConstraint) {
        return hasMetricTimeFilter(filters) || hasMetricTimeConstraint(timeConstraint);
    }

    public static void requireMetricTimeCondition(List<DatasetFilter> filters) {
        if (!hasMetricTimeFilter(filters)) {
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, REQUIRED_MESSAGE);
        }
    }

    public static boolean isMetricTimeField(String field) {
        return field != null && (AloudataFilterExpressions.METRIC_TIME_FIELD.equals(field)
                || field.toLowerCase(Locale.ROOT).matches("metric_time__[a-z0-9_]+"));
    }

    private static boolean hasValue(Object value) {
        if (value == null) return false;
        if (value instanceof CharSequence text) return !text.toString().isBlank();
        if (value instanceof Collection<?> values) return !values.isEmpty() && values.stream().allMatch(MetricTimeQueryGuard::hasValue);
        return true;
    }

    private static boolean hasMetricTimeExpression(String expression) {
        return expression != null && METRIC_TIME_REFERENCE.matcher(expression).find()
                && COMPARISON.matcher(expression).find();
    }
}
