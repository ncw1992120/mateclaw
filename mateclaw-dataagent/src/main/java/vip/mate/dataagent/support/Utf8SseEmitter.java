package vip.mate.dataagent.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

/**
 * SSE emitter that explicitly advertises charset=UTF-8 on Content-Type header.
 * <p>
 * Prevents Chinese character mojibake on Windows/GBK locales and certain
 * reverse proxies that transcode based on system locale when no explicit
 * charset is specified on text/event-stream.
 */
public class Utf8SseEmitter extends SseEmitter {

    private static final MediaType TEXT_EVENT_STREAM_UTF8 =
            new MediaType("text", "event-stream", StandardCharsets.UTF_8);

    public Utf8SseEmitter() {
        super();
    }

    public Utf8SseEmitter(Long timeoutMillis) {
        super(timeoutMillis);
    }

    @Override
    protected void extendResponse(ServerHttpResponse response) {
        super.extendResponse(response);
        HttpHeaders headers = response.getHeaders();
        MediaType contentType = headers.getContentType();
        if (contentType == null || contentType.getCharset() == null) {
            headers.setContentType(TEXT_EVENT_STREAM_UTF8);
        }
    }
}
