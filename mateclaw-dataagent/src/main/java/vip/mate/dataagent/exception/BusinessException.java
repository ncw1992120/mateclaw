package vip.mate.dataagent.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 携带 HTTP 状态码的业务异常，用于 Service 层向 Controller 层传递错误信息。
 * 由 {@link DataAgentGlobalExceptionHandler} 统一捕获并转换为 R 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 构造业务异常
     *
     * @param code 状态码
     * @param msg  错误信息
     */
    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
