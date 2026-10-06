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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import vip.mate.common.result.R;
import vip.mate.dataagent.dataset.DatasetReadException;
import vip.mate.exception.MateClawException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** 兼容轻量单测与手动构造场景；Spring 使用必需依赖注入的主构造器。 */
    public DataAgentGlobalExceptionHandler() {
        this(new ObjectMapper());
    }

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
     * 处理请求体反序列化异常（400）：QueryContext 等契约 DTO 的字段校验在构造器中抛出，
     * Jackson 包装为 HttpMessageNotReadableException —— 必须映射 400 稳定信封而非 500。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        String detail = e.getCause() instanceof Exception cause ? cause.getMessage() : e.getMessage();
        log.warn("请求体格式异常: {}", detail);
        return ResponseEntity.badRequest().body(R.fail(400, String.valueOf(detail)));
    }

    /**
     * 处理查询链路业务异常：携带稳定错误码（QUERY_CONTEXT_INVALID 等），映射 400 稳定信封。
     */
    @ExceptionHandler(vip.mate.dataagent.service.QueryPlanException.class)
    public ResponseEntity<R<Void>> handleQueryPlanException(vip.mate.dataagent.service.QueryPlanException e) {
        log.warn("查询链路业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.badRequest().body(R.fail(400, e.getCode() + ": " + e.getMessage()));
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
     * 处理数据集读取异常：按错误码映射 HTTP 状态，避免统一兜底成 500。
     * <p>
     * 典型场景：指标视图无权限（Aloudata SM_02_0038 → ACCESS_DENIED）应返回 403 + 中文提示，
     * 而非 500「服务器内部错误」。
     */
    @ExceptionHandler(DatasetReadException.class)
    public ResponseEntity<R<Void>> handleDatasetReadException(DatasetReadException e) {
        HttpStatus httpStatus = switch (e.code()) {
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_REQUEST, UNSUPPORTED_FILTER -> HttpStatus.BAD_REQUEST;
            case SCHEMA_MISMATCH -> HttpStatus.CONFLICT;
            case RESULT_LIMIT_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            case SOURCE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case SOURCE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        String message = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : "数据集读取失败";
        log.warn("数据集读取异常: code={}, msg={}", e.code(), message);
        return ResponseEntity.status(httpStatus).body(R.fail(httpStatus.value(), message));
    }

    /**
     * 处理上游调用失败：连接层不可达（含本地 mock 服务没起）返回 503 而非 500
     * <p>
     * 典型场景：local-mock 模式把请求发给本地 mock 服务，若该服务未启动，RestTemplate 会抛
     * {@link ResourceAccessException}（{@code Connection refused}），此前会被兜底成
     * 500「服务器内部错误」，看不出是上游不可达还是代码 bug。
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<R<Void>> handleResourceAccessException(ResourceAccessException e) {
        String target = extractUpstreamTarget(e.getMessage());
        String message = "上游服务不可达" + (target == null ? "" : "：" + target)
                + "。请确认上游地址可访问；使用 local-mock 内置夹具时请清空 ALOUDATA_MOCK_SERVER";
        log.warn("上游不可达: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(R.fail(503, message));
    }

    /**
     * 其他 RestClient 异常（上游返回非 2xx、响应不可解析等）按网关错误返回，避免与本地故障混淆。
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<R<Void>> handleRestClientException(RestClientException e) {
        String target = extractUpstreamTarget(e.getMessage());
        String message = "上游调用失败" + (target == null ? "" : "：" + target) + "。"
                + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        log.warn("上游调用失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(R.fail(502, message));
    }

    /** 从 RestTemplate 异常消息中提取目标 URL（形如 {@code for "http://host:port/path"}）。 */
    private String extractUpstreamTarget(String message) {
        if (message == null) return null;
        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(message);
        return matcher.find() ? matcher.group(1) : null;
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
