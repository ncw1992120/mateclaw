package vip.mate.dataagent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vip.mate.common.result.R;

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
     * 兜底：捕获所有未处理的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleException(Exception e) {
        log.error("未预期的异常: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务器内部错误: " + (e.getMessage() != null ? e.getMessage() : "未知异常")));
    }
}
