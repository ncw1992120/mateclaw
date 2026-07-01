package vip.mate.dataagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;

/**
 * DataAgent 全局异常处理器
 * <p>
 * 由于 DataAgentApplication 的 scanBasePackages 仅限 vip.mate.dataagent，
 * mateclaw-server 的 GlobalExceptionHandler（vip.mate.exception）不会被扫描到，
 * 因此需要在本模块内定义异常处理器，确保所有异常都以 R 格式返回。
 */
@Slf4j
@RestControllerAdvice
public class DataAgentGlobalExceptionHandler {

    /**
     * 处理业务异常（携带状态码）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        HttpStatus httpStatus = HttpStatus.resolve(e.getCode());
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(httpStatus).body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常（400）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return ResponseEntity.badRequest().body(R.fail(400, e.getMessage()));
    }

    /**
     * 处理状态冲突异常（409）
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<R<Void>> handleIllegalStateException(IllegalStateException e) {
        log.warn("状态异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(R.fail(409, e.getMessage()));
    }

    /**
     * 处理 MateClawException（来自 mateclaw-server SDK 的异常）
     */
    @ExceptionHandler(MateClawException.class)
    public ResponseEntity<R<Void>> handleMateClawException(MateClawException e) {
        int code = e.getCode() > 0 ? e.getCode() : 500;
        log.warn("MateClaw 异常: code={}, msg={}", code, e.getMessage());
        HttpStatus httpStatus = HttpStatus.resolve(code);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(httpStatus).body(R.fail(code, e.getMessage()));
    }

    /**
     * 兜底：捕获所有未处理的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        log.error("未预期的异常: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务器内部错误: " + (e.getMessage() != null ? e.getMessage() : "未知异常")));
    }
}
