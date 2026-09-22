package vip.mate.dataagent.service;

/**
 * 查询计划/结果集链路的业务异常：携带稳定错误码，由 Controller 统一映射为 R&lt;T&gt; 错误信封。
 * message 面向调用方可解释，不包含凭据、连接串或令牌。
 */
public class QueryPlanException extends RuntimeException {
    private final String code;

    public QueryPlanException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? QueryPlanErrorCodes.QUERY_CONTEXT_INVALID : code;
    }

    public static QueryPlanException of(String code, String message) {
        return new QueryPlanException(code, message);
    }

    public String getCode() {
        return code;
    }
}
