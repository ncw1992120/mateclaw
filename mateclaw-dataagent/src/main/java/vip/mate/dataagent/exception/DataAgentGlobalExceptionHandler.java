package vip.mate.dataagent.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;

import java.util.Map;

/**
 * DataAgent 全局异常处理器
 * <p>
 * 由于 DataAgentApplication 的 scanBasePackages 仅限 vip.mate.dataagent，
 * mateclaw-server 的 GlobalExceptionHandler（vip.mate.exception）不会被扫描到，
 * 因此需要在本模块内定义异常处理器，确保所有异常都以 R 格式返回。
 * <p>
 * SSE 端点（如 /v1/chat/stream）声明 produces=text/event-stream，若对其异常
 * 返回 JSON 响应，会与请求 Accept: text/event-stream 协商冲突触发 406，
 * 前端无法获知具体错误（如限流 429 / 熔断 503）。故对 SSE 请求返回与流内
 * 广播一致的 error/done 事件帧（HTTP 状态码保留），前端按既有错误逻辑处理。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class DataAgentGlobalExceptionHandler {

    /** SSE 错误事件名（与流内广播的 error 事件保持一致） */
    private static final String SSE_EVENT_ERROR = "error";

    /** SSE 结束事件名（与流内广播的 done 事件保持一致） */
    private static final String SSE_EVENT_DONE = "done";

    private final ObjectMapper objectMapper;

    /**
     * 处理业务异常（携带状态码）
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return buildErrorResponse(request, e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（400）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("参数异常: {}", e.getMessage());
        return buildErrorResponse(request, 400, e.getMessage());
    }

    /**
     * 处理状态冲突异常（409）
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        log.warn("状态异常: {}", e.getMessage());
        return buildErrorResponse(request, 409, e.getMessage());
    }

    /**
     * 处理 MateClawException（来自 mateclaw-server SDK 的异常）
     */
    @ExceptionHandler(MateClawException.class)
    public ResponseEntity<?> handleMateClawException(MateClawException e, HttpServletRequest request) {
        int code = e.getCode() > 0 ? e.getCode() : 500;
        log.warn("MateClaw 异常: code={}, msg={}", code, e.getMessage());
        return buildErrorResponse(request, code, e.getMessage());
    }

    /**
     * 兜底：捕获所有未处理的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e, HttpServletRequest request) {
        log.error("未预期的异常: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return buildErrorResponse(request, 500, "服务器内部错误: " + (e.getMessage() != null ? e.getMessage() : "未知异常"));
    }

    /**
     * 构建错误响应：SSE 请求返回 SSE 错误帧，其余返回标准 R JSON 格式
     */
    private ResponseEntity<?> buildErrorResponse(HttpServletRequest request, int code, String message) {
        if (isSseRequest(request)) {
            return buildSseErrorResponse(code, message);
        }
        HttpStatus httpStatus = HttpStatus.resolve(code);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(httpStatus).body(R.fail(code, message));
    }

    /**
     * 判断请求是否为 SSE 请求（Accept 头包含 text/event-stream）
     */
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * 构建 SSE 格式错误响应：error 事件携带 code/message，done 事件携带 failed 状态，
     * 前端按既有 SSE 错误处理逻辑终止加载并提示具体原因
     */
    private ResponseEntity<String> buildSseErrorResponse(int code, String message) {
        String errorData = toJson(Map.of("code", code, "message", message));
        String doneData = toJson(Map.of("status", "failed"));
        String body = "event: " + SSE_EVENT_ERROR + "\ndata: " + errorData + "\n\n"
                + "event: " + SSE_EVENT_DONE + "\ndata: " + doneData + "\n\n";
        HttpStatus status = HttpStatus.resolve(code);
        return ResponseEntity.status(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    /**
     * 序列化为 JSON（失败时回退为最小化占位对象）
     */
    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{\"message\":\"serialization_error\"}";
        }
    }
}
