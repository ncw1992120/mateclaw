package vip.mate.dataagent.aloudata;

import org.junit.jupiter.api.Test;
import vip.mate.dataagent.dataset.DatasetFilter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式生成是「本地 mock 与正式环境行为一致」的关键契约：
 * 真实 semantic metrics/query 只接受表达式字符串，结构化对象会 SM99002。
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
}
