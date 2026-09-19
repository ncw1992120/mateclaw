package vip.mate.dataagent.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上游故障的状态码映射：连接层不可达必须是 503（而不是兜底 500「服务器内部错误」），
 * 否则本地 mock 服务没起时，用户看到的报错无法指向真正原因。
 */
class DataAgentGlobalExceptionHandlerTest {

    private final DataAgentGlobalExceptionHandler handler = new DataAgentGlobalExceptionHandler();

    @Test
    void unreachableUpstreamBecomesServiceUnavailableWithTarget() {
        ResourceAccessException error = new ResourceAccessException(
                "I/O error on GET request for \"http://127.0.0.1:18081/anymetrics/api/v1/analysisview/list\": "
                        + "Connection refused");

        var response = handler.handleResourceAccessException(error);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().getCode());
        String message = response.getBody().getMsg();
        assertTrue(message.contains("http://127.0.0.1:18081/anymetrics/api/v1/analysisview/list"), message);
        assertTrue(message.contains("mock 服务"), message);
    }

    @Test
    void otherRestClientFailureBecomesBadGateway() {
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        var response = handler.handleRestClientException(error);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(502, response.getBody().getCode());
    }

    @Test
    void connectionIssueIsNotReportedAsInternalServerError() {
        // 兜底处理器会返回 500；这里确保连接层故障走的是 503 分支
        var response = handler.handleResourceAccessException(new ResourceAccessException("connect timed out"));
        assertNotEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
