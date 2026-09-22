package vip.mate.dataagent.service;

/**
 * 查询链路稳定业务错误码（见实施计划「统一接口契约 §4」）。
 * 异常封装统一映射这些码，前端据此提示，不透出堆栈。
 */
public final class QueryPlanErrorCodes {
    public static final String QUERY_CONTEXT_INVALID = "QUERY_CONTEXT_INVALID";
    public static final String FIELD_NOT_ALLOWED = "FIELD_NOT_ALLOWED";
    public static final String SORT_NOT_ALLOWED = "SORT_NOT_ALLOWED";
    public static final String PAGE_SIZE_EXCEEDED = "PAGE_SIZE_EXCEEDED";
    public static final String DATASET_ACCESS_DENIED = "DATASET_ACCESS_DENIED";
    public static final String PREPARED_INPUT_EXPIRED = "PREPARED_INPUT_EXPIRED";
    public static final String RESULT_NOT_READY = "RESULT_NOT_READY";

    private QueryPlanErrorCodes() {
    }
}
