package vip.mate.llm.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import vip.mate.exception.MateClawException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rerank 模型公共基类
 * <p>
 * 统一承载：RestClient 构造（Bearer 认证 + 超时）、请求发送、响应解析。
 * 子类只需实现 {@link #resolveEndpoint()} 与 {@link #buildRequestBody(RerankRequest)}，
 * 以适配不同供应商的端点路径与请求体格式。
 *
 * @author MateClaw Team
 */
@Slf4j
public abstract class AbstractRerankModel implements RerankModel {

    /** HTTP 超时 */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    protected final String apiKey;
    protected final String baseUrl;
    protected final String modelName;
    protected final ObjectMapper objectMapper;
    protected final RestClient restClient;

    /**
     * 构造基类
     *
     * @param apiKey       API Key
     * @param baseUrl      API 基础地址（可空，子类决定 fallback）
     * @param modelName    模型名
     * @param objectMapper Jackson 序列化器
     */
    protected AbstractRerankModel(String apiKey, String baseUrl, String modelName, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.objectMapper = objectMapper;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public List<RerankResult> rerank(RerankRequest request) {
        if (request == null || !StringUtils.hasText(request.getQuery())) {
            throw new MateClawException("err.rerank.invalid_request", "Rerank 请求缺少 query");
        }
        if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
            return List.of();
        }

        Map<String, Object> body = buildRequestBody(request);
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(resolveEndpoint())
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new MateClawException("err.rerank.call_failed",
                    "Rerank 调用失败: " + e.getMessage());
        }
        return parseResults(responseBody);
    }

    /**
     * 构建请求体
     *
     * @param request 重排请求
     * @return 请求体 Map（由 Jackson 序列化）
     */
    protected abstract Map<String, Object> buildRequestBody(RerankRequest request);

    /**
     * 解析端点地址
     *
     * @return 完整请求 URL
     */
    protected abstract String resolveEndpoint();

    /**
     * 解析 rerank 响应中的 results 数组
     * <p>
     * 通用结构：{"results":[{"index":0,"relevance_score":0.9}]}，
     * DashScope 为 {"output":{"results":[...]}}，由子类通过 {@link #extractResultsNode} 指定。
     */
    private List<RerankResult> parseResults(String responseBody) {
        List<RerankResult> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode resultsNode = extractResultsNode(root);
            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    int index = node.path("index").asInt(-1);
                    double score = node.path("relevance_score").asDouble(0.0);
                    if (index >= 0) {
                        results.add(new RerankResult(index, score, null));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Rerank] 解析响应失败: {}", e.getMessage());
            throw new MateClawException("err.rerank.parse_failed",
                    "Rerank 响应解析失败: " + e.getMessage());
        }
        return results;
    }

    /**
     * 从响应根节点提取 results 数组节点
     *
     * @param root 响应根节点
     * @return results 数组节点
     */
    protected JsonNode extractResultsNode(JsonNode root) {
        return root.path("results");
    }
}
