package vip.mate.dataagent.util;

import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.config.QLExpressRunStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * QLExpress 表达式求值通用工具
 * <p>
 * 封装 QLExpress 引擎的初始化与求值入口，调用方只需提供表达式字符串与
 * 变量 Map（变量名 → 值），不感知引擎类型。适用于动态规则判断等场景。
 * <p>
 * 引擎特性：共享单例 {@link ExpressRunner}（线程安全，运算临时变量为 ThreadLocal，
 * 编译缓存由 execute(isCache=true) 复用）；初始化时禁止表达式调用高危系统 API
 * （Runtime/ProcessBuilder/System.exit 等），做纵深防御。
 * <p>
 * 注意：引擎保持原生语义，内置运算符/方法对 null 操作数会抛
 * 「对象为空，不能执行方法」异常（由 {@link #evaluateBoolean(String, Map, boolean)}
 * 的兜底机制承接）。调用方若不希望空值触发异常，应在构建变量 Map 时自行归一化
 * （如 null → 空字符串），见 AloudataSyncFilterSupport 的上下文构建。
 */
@Slf4j
public final class QlExpressSupport {

    /** QLExpress 运行器（线程安全） */
    private static final ExpressRunner RUNNER;

    static {
        QLExpressRunStrategy.setForbidInvokeSecurityRiskMethods(true);
        RUNNER = new ExpressRunner();
    }

    private QlExpressSupport() {
    }

    /**
     * 求值表达式，返回原始结果
     *
     * @param expression QLExpress 表达式
     * @param variables  变量 Map（变量名 → 值），可为 null
     * @return 表达式求值结果（可能是任意类型，由表达式决定）
     * @throws IllegalStateException 表达式语法错误或执行失败
     */
    public static Object evaluate(String expression, Map<String, Object> variables) {
        DefaultContext<String, Object> context = new DefaultContext<>();
        if (variables != null) {
            context.putAll(variables);
        }
        try {
            return RUNNER.execute(expression, context, null, false, true);
        } catch (Exception e) {
            throw new IllegalStateException("QLExpress 表达式执行失败: " + expression, e);
        }
    }

    /**
     * 求值布尔表达式
     * <p>
     * 结果为 Boolean.TRUE 返回 true；其他结果（含 null/非布尔）返回 false；
     * 执行异常时告警并返回兜底值（兜底语义由调用方业务决定，如黑名单场景传 false 实现 fail-open）。
     *
     * @param expression QLExpress 布尔表达式
     * @param variables  变量 Map（变量名 → 值），可为 null
     * @param fallback   执行异常时的兜底返回值
     * @return 表达式求值结果
     */
    public static boolean evaluateBoolean(String expression, Map<String, Object> variables, boolean fallback) {
        try {
            return Boolean.TRUE.equals(evaluate(expression, variables));
        } catch (Exception e) {
            Throwable cause = e.getCause();
            log.warn("QLExpress 布尔表达式执行失败，返回兜底值 {}: {} - 根因: {}",
                    fallback, expression, cause != null ? cause.toString() : e.getMessage(), e);
            return fallback;
        }
    }
}
