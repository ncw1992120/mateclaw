package vip.mate.dataagent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QLExpress 表达式求值通用工具单元测试
 */
class QlExpressSupportTest {

    @Test
    @DisplayName("evaluate 返回原始结果（字符串与算术）")
    void evaluateReturnsRawResult() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "测试类目");
        assertThat(QlExpressSupport.evaluate("name + '!'", vars)).isEqualTo("测试类目!");
        assertThat(QlExpressSupport.evaluate("1 + 2 * 3", null)).isEqualTo(7);
        assertThat(QlExpressSupport.evaluate("name == '测试类目'", vars)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("evaluateBoolean：TRUE 为真，null/非布尔为假")
    void evaluateBooleanSemantics() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("categoryName", "敏感数据");
        vars.put("emptyName", (Object) null);

        assertThat(QlExpressSupport.evaluateBoolean("categoryName in ('测试类目', '敏感数据')", vars, false)).isTrue();
        assertThat(QlExpressSupport.evaluateBoolean("categoryName in ('测试类目', '敏感数据')", vars, true)).isTrue();
        assertThat(QlExpressSupport.evaluateBoolean("categoryName == '其他'", vars, true)).isFalse();
        assertThat(QlExpressSupport.evaluateBoolean("emptyName != null && emptyName.contains('x')", vars, true)).isFalse();
    }

    @Test
    @DisplayName("执行异常时 evaluateBoolean 返回兜底值，evaluate 抛出异常")
    void errorHandling() {
        assertThat(QlExpressSupport.evaluateBoolean("categoryName.contains(", null, false)).isFalse();
        assertThat(QlExpressSupport.evaluateBoolean("categoryName.contains(", null, true)).isTrue();
        assertThatThrownBy(() -> QlExpressSupport.evaluate("undefinedVar.startsWith('x')", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QLExpress 表达式执行失败");
    }

    @Test
    @DisplayName("引擎保持原生语义：null 操作数抛异常由兜底承接，归一化由调用方负责")
    void stockNullOperandSemantics() {
        /* 变量缺失（空上下文）→ in 对 null 操作数按引擎原生行为抛异常 */
        assertThatThrownBy(() -> QlExpressSupport.evaluate("categoryName in ('未分类')", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QLExpress 表达式执行失败")
                .hasRootCauseMessage("对象为空，不能执行方法:in");

        /* evaluateBoolean 走兜底，不向调用方抛出 */
        assertThat(QlExpressSupport.evaluateBoolean("categoryName in ('未分类')", Map.of(), false)).isFalse();
        assertThat(QlExpressSupport.evaluateBoolean("categoryName in ('未分类')", Map.of(), true)).isTrue();
    }

    @Test
    @DisplayName("共享 runner 多线程并发求值无异常（含编译缓存）")
    void concurrentEvaluation() throws Exception {
        String[] expressions = {
            "categoryName in ('未分类')",
            "categoryName in ('测试类目', '敏感数据')",
            "metricName.startsWith('test_')",
        };
        java.util.List<String> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Runnable task = () -> {
            for (int i = 0; i < 200; i++) {
                String expression = expressions[i % expressions.length];
                try {
                    QlExpressSupport.evaluate(expression,
                            Map.of("categoryName", "未分类", "metricName", "test_x"));
                } catch (Exception e) {
                    errors.add(expression + " => " + e.getCause());
                }
            }
        };
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            threads.add(new Thread(task));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(errors).as("并发求值不应出现异常: %s", errors.stream().limit(3).toList()).isEmpty();
    }
}
