package vip.mate.dataagent.aloudata;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式生成是「本地 mock 与正式环境行为一致」的关键契约：
 * 真实 semantic metrics/query 只接受表达式字符串，结构化对象会 SM99002；
 * metric_time（分区字段）字段引用必须单引号，条件按 DateTrunc/Cast 形态生成
 * （demo 环境实测：无引号/双引号字段一律 SM_02_0006/0014）。
 */
class AloudataFilterExpressionsTest {

    @Test
    void normalizesSdkEnumsAndSymbols() {
        assertEquals("=", AloudataFilterExpressions.symbol("eq"));
        assertEquals("=", AloudataFilterExpressions.symbol("="));
        assertEquals("<>", AloudataFilterExpressions.symbol("!="));
        assertEquals(">=", AloudataFilterExpressions.symbol("gte"));
        assertEquals("IN", AloudataFilterExpressions.symbol("in"));
        assertEquals("NotIn", AloudataFilterExpressions.symbol("not_in"));
        assertNull(AloudataFilterExpressions.symbol("contains"));
    }

    @Test
    void quotesStringsAndKeepsNumbersBare() {
        assertEquals("[region] = \"华东\"", AloudataFilterExpressions.of("region", "eq", "华东"));
        assertEquals("[amount] >= 10", AloudataFilterExpressions.of("amount", "gte", 10));
        assertEquals("[ok] = true", AloudataFilterExpressions.of("ok", "=", true));
        assertEquals("[name] = \"a\\\"b\"", AloudataFilterExpressions.of("name", "eq", "a\"b"));
    }

    @Test
    void buildsInNotInAndBetween() {
        assertEquals("[region] IN (\"华东\",\"华南\")", AloudataFilterExpressions.of("region", "in", List.of("华东", "华南")));
        assertEquals("[region] NotIn (\"华东\")", AloudataFilterExpressions.of("region", "not_in", List.of("华东")));
        assertEquals("([amount] >= 10 AND [amount] <= 40)",
                AloudataFilterExpressions.of(new DatasetFilter("amount", "measure", "between", List.of(10, 40))));
    }

    @Test
    void acceptsMapFormIncludingOpAlias() {
        assertEquals("[channel] = \"APP\"",
                AloudataFilterExpressions.of(Map.of("field", "channel", "op", "=", "value", "APP")));
        assertEquals("[channel] = \"APP\"",
                AloudataFilterExpressions.of(Map.of("field", "channel", "operator", "eq", "value", "APP")));
        assertNull(AloudataFilterExpressions.of(Map.of("value", "APP")));
    }

    @Test
    void unsupportedOperatorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AloudataFilterExpressions.of("region", "contains", "x"));
    }

    @Test
    void metricTimeUsesSingleQuotedDateTruncPartitionForm() {
        // 分区字段写法（图中形态，demo 实测通过）：单引号字段 + DateTrunc/Cast + 值补 00:00:00
        assertEquals("(DateTrunc(['metric_time'], \"DAY\") >= (DateTrunc(Cast(\"2026-09-01 00:00:00\", \"TIMESTAMP\"), \"DAY\")))",
                AloudataFilterExpressions.of("metric_time", "gte", "2026-09-01"));
        assertEquals("(DateTrunc(['metric_time'], \"DAY\") = (DateTrunc(Cast(\"2026-08-26 00:00:00\", \"TIMESTAMP\"), \"DAY\")))",
                AloudataFilterExpressions.of("metric_time", "eq", "2026-08-26"));
    }

    @Test
    void metricTimeBetweenMergesBothBoundsIntoSingleExpression() {
        assertEquals("(DateTrunc(['metric_time'], \"DAY\") >= (DateTrunc(Cast(\"2024-01-12 00:00:00\", \"TIMESTAMP\"), \"DAY\"))"
                        + " AND DateTrunc(['metric_time'], \"DAY\") <= (DateTrunc(Cast(\"2024-01-26 00:00:00\", \"TIMESTAMP\"), \"DAY\")))",
                AloudataFilterExpressions.of(new DatasetFilter("metric_time", "dimension", "between", List.of("2024-01-12", "2024-01-26"))));
    }

    @Test
    void metricTimeRejectsSetOperatorsOnPartitionField() {
        assertThrows(IllegalArgumentException.class,
                () -> AloudataFilterExpressions.of("metric_time", "in", List.of("2026-09-01")));
    }

    @Test
    void plainFieldBetweenStillUsesSimpleForm() {
        assertEquals("([order_date] >= \"2026-09-01\" AND [order_date] <= \"2026-09-30\")",
                AloudataFilterExpressions.of("order_date", "between", List.of("2026-09-01", "2026-09-30")));
    }
}
