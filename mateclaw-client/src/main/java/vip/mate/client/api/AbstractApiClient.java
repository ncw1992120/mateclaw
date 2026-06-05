package vip.mate.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * API 客户端抽象基类
 * <p>
 * 封装 RestTemplate 调用逻辑，提供通用的 GET/POST/PUT/DELETE/PATCH 请求方法
 */
public abstract class AbstractApiClient {

    /** 服务端基础地址 */
    private final String baseUrl;

    /** RestTemplate 实例 */
    private final RestTemplate restTemplate;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AbstractApiClient(String baseUrl, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * 解析路径变量
     * <p>
     * 将路径中的占位符（如 {id}、{agentId}）替换为实际值
     *
     * @param path       带占位符的路径
     * @param pathValues 路径变量值，按顺序替换占位符
     * @return 替换后的路径
     */
    protected String resolvePath(String path, Object... pathValues) {
        if (pathValues == null || pathValues.length == 0) {
            return path;
        }
        String result = path;
        for (Object value : pathValues) {
            if (value != null) {
                result = result.replaceFirst("\\{[^}]+\\}", String.valueOf(value));
            }
        }
        return result;
    }

    /**
     * 构建完整请求地址
     *
     * @param path 请求路径
     * @return 完整 URL
     */
    protected String buildUrl(String path) {
        return baseUrl + path;
    }

    /**
     * 构建带查询参数的完整请求地址
     *
     * @param path      请求路径
     * @param params    查询参数
     * @return 完整 URL
     */
    protected String buildUrl(String path, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return buildUrl(path);
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append(path);
        sb.append("?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * 发送 GET 请求
     *
     * @param path          请求路径
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T get(String path, ParameterizedTypeReference<T> typeReference) {
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.GET,
                null,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送带查询参数的 GET 请求
     *
     * @param path          请求路径
     * @param params        查询参数
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T get(String path, Map<String, Object> params, ParameterizedTypeReference<T> typeReference) {
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path, params),
                HttpMethod.GET,
                null,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送 POST 请求
     *
     * @param path          请求路径
     * @param body          请求体
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T post(String path, Object body, ParameterizedTypeReference<T> typeReference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.POST,
                entity,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送 PUT 请求
     *
     * @param path          请求路径
     * @param body          请求体
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T put(String path, Object body, ParameterizedTypeReference<T> typeReference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.PUT,
                entity,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送不带请求体的 PUT 请求
     *
     * @param path          请求路径
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T put(String path, ParameterizedTypeReference<T> typeReference) {
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.PUT,
                null,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送 DELETE 请求
     *
     * @param path          请求路径
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T delete(String path, ParameterizedTypeReference<T> typeReference) {
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.DELETE,
                null,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送 PATCH 请求
     *
     * @param path          请求路径
     * @param body          请求体
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T patch(String path, Object body, ParameterizedTypeReference<T> typeReference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.PATCH,
                entity,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送带请求体的 DELETE 请求
     *
     * @param path          请求路径
     * @param body          请求体
     * @param typeReference 返回类型引用
     * @param <T>           返回数据类型
     * @return 响应结果
     */
    protected <T> T delete(String path, Object body, ParameterizedTypeReference<T> typeReference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(
                buildUrl(path),
                HttpMethod.DELETE,
                entity,
                typeReference
        );
        return response.getBody();
    }

    /**
     * 发送 SSE 流式 POST 请求，逐行读取服务端推送的 SSE 事件
     * <p>
     * 使用 restTemplate.execute() 直接获取底层连接的 InputStream，
     * 避免使用 Resource.class 导致的 readTimeout 超时问题。
     * 支持标准 SSE 格式（event: / data: / id: 字段）。
     *
     * @param path     请求路径
     * @param body     请求体
     * @param callback SSE 事件回调
     */
    protected void postForSseStream(String path, Object body, SseStreamCallback callback) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        executeSseStream(HttpMethod.POST, buildUrl(path), headers, body, callback);
    }

    /**
     * 发送 SSE 流式 GET 请求，逐行读取服务端推送的 SSE 事件
     * <p>
     * 用于 Wiki 进度等 SSE 订阅接口，调用方通过回调处理流式事件
     *
     * @param path     请求路径
     * @param callback SSE 事件回调
     */
    protected void getForSseStream(String path, SseStreamCallback callback) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        executeSseStream(HttpMethod.GET, buildUrl(path), headers, null, callback);
    }

    /**
     * 执行 SSE 流式请求，逐行解析事件
     * <p>
     * 使用 Java 原生 HttpURLConnection 直接发起请求，避免 RestTemplate 的 readTimeout 限制
     * 和 Resource.class 不兼容 text/event-stream 的问题。
     * 认证头从 RestTemplate 的拦截器链中提取 TokenProvider 的当前 token 来手动添加。
     * <p>
     * 解析规则：
     * - event: xxx → 记录事件名称
     * - data: xxx → 记录事件数据
     * - 空行 → 表示一个完整事件结束，触发回调
     * - event 名为 "done" 时触发 onComplete
     * - event 名为 "error" 时触发 onError
     *
     * @param method   HTTP 方法
     * @param url      完整 URL
     * @param headers  请求头
     * @param body     请求体（可为 null）
     * @param callback SSE 事件回调
     */
    private void executeSseStream(HttpMethod method, String url, HttpHeaders headers,
                                  Object body, SseStreamCallback callback) {
        try {
            // 先序列化请求体，计算 Content-Length
            byte[] bodyBytes = null;
            if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) {
                bodyBytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            }

            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod(method.name());
            connection.setDoInput(true);
            connection.setConnectTimeout(5000);
            // SSE 流可能持续几分钟，设为 0 表示无限读取超时
            connection.setReadTimeout(0);

            // 复制 RestTemplate 拦截器链中的认证头到 headers
            copyAuthHeaders(headers);

            // 设置请求头
            connection.setRequestProperty("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
            connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String value : entry.getValue()) {
                    connection.setRequestProperty(entry.getKey(), value);
                }
            }

            // 写入请求体（显式设置 Content-Length 避免 chunked encoding）
            if (bodyBytes != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bodyBytes.length);
                connection.getOutputStream().write(bodyBytes);
                connection.getOutputStream().flush();
                connection.getOutputStream().close();
            }

            // 读取响应
            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                String errorBody = readErrorBody(connection);
                callback.onError(new RuntimeException(
                        "SSE request failed: HTTP " + responseCode + " - " + errorBody));
                connection.disconnect();
                return;
            }

            // 逐行解析 SSE 事件
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String eventName = null;
                String eventData = null;
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        eventData = line.substring(5).trim();
                    } else if (line.isEmpty()) {
                        // 空行 = 一个完整 SSE 事件结束
                        if (eventName != null || eventData != null) {
                            dispatchSseEvent(eventName, eventData, callback);
                        }
                        eventName = null;
                        eventData = null;
                    }
                }
                // 处理流结束时可能残留的最后一个事件（无尾空行）
                if (eventName != null || eventData != null) {
                    dispatchSseEvent(eventName, eventData, callback);
                }
                callback.onComplete();
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * 从 RestTemplate 的拦截器链中提取认证头信息
     * <p>
     * RestTemplate 的 TokenAuthInterceptor 会添加 Authorization 和 X-Workspace-Id 头，
     * 但 HttpURLConnection 不经过拦截器链，所以需要手动添加。
     * 这里通过反射从拦截器中提取 token 和 workspaceId。
     *
     * @param headers 需要补充认证头的 HttpHeaders
     */
    private void copyAuthHeaders(HttpHeaders headers) {
        for (ClientHttpRequestInterceptor interceptor : restTemplate.getInterceptors()) {
            if (interceptor.getClass().getSimpleName().equals("TokenAuthInterceptor")) {
                try {
                    // TokenAuthInterceptor 是 record，通过反射获取字段值
                    var fields = interceptor.getClass().getDeclaredFields();
                    var tokenProviderField = fields[0];
                    tokenProviderField.setAccessible(true);
                    var tokenProvider = tokenProviderField.get(interceptor);

                    var propertiesField = fields[1];
                    propertiesField.setAccessible(true);
                    var properties = propertiesField.get(interceptor);

                    // 获取 token
                    var getTokenMethod = tokenProvider.getClass().getMethod("getToken");
                    String token = (String) getTokenMethod.invoke(tokenProvider);
                    if (token != null && !token.isBlank()) {
                        headers.set("Authorization", "Bearer " + token);
                    }

                    // 获取 workspaceId
                    var getWorkspaceIdMethod = properties.getClass().getMethod("getDefaultWorkspaceId");
                    Long workspaceId = (Long) getWorkspaceIdMethod.invoke(properties);
                    if (workspaceId != null) {
                        headers.set("X-Workspace-Id", String.valueOf(workspaceId));
                    }
                } catch (Exception e) {
                    // 反射失败时静默忽略，SSE 请求将不带认证头
                }
                break;
            }
        }
    }

    /**
     * 读取 HTTP 错误响应体
     */
    private String readErrorBody(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "[no body]";
        }
    }

    /**
     * 分发 SSE 事件到回调
     *
     * @param eventName 事件名称（可能为 null）
     * @param eventData 事件数据（可能为 null）
     * @param callback  回调接口
     */
    private void dispatchSseEvent(String eventName, String eventData, SseStreamCallback callback) {
        if ("done".equals(eventName)) {
            callback.onComplete();
        } else if ("error".equals(eventName) && eventData != null) {
            callback.onError(new RuntimeException("SSE error: " + eventData));
        } else if (eventData != null) {
            callback.onData(eventData);
        }
    }

    /**
     * SSE 流式回调接口
     */
    public interface SseStreamCallback {

        /** 接收数据事件 */
        void onData(String data);

        /** 流结束 */
        void onComplete();

        /** 发生错误 */
        void onError(Exception e);
    }
}
