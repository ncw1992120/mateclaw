package vip.mate.llm.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DashScope 原生 Rerank 模型实现（gte-rerank 系列）
 * <p>
 * 调用 DashScope 原生端点 {@code /api/v1/services/rerank/text-rerank/text-rerank}。
 * Spring AI Alibaba SDK（1.x）尚未封装 rerank，故走原生 HTTP。
 *
 * @author MateClaw Team
 */
public class DashScopeRerankModel extends AbstractRerankModel {

    /** DashScope 默认 API 基础地址 */
    static final String DASHSCOPE_DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/api/v1";

    /** rerank 端点路径后缀 */
    private static final String RERANK_PATH = "/services/rerank/text-rerank/text-rerank";

    /**
     * 构造 DashScope Rerank 模型
     *
     * @param apiKey       DashScope API Key
     * @param baseUrl      API 基础地址（可空，空则用 DashScope 默认地址）
     * @param modelName    模型名（如 gte-rerank / gte-rerank-v2）
     * @param objectMapper Jackson 序列化器
     */
    public DashScopeRerankModel(String apiKey, String baseUrl, String modelName, ObjectMapper objectMapper) {
        super(apiKey, baseUrl, modelName, objectMapper);
    }

    @Override
    protected Map<String, Object> buildRequestBody(RerankRequest request) {
        int topN = request.getTopN() != null && request.getTopN() > 0
                ? request.getTopN() : request.getDocuments().size();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", request.getQuery());
        input.put("documents", request.getDocuments());
        body.put("input", input);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("top_n", topN);
        parameters.put("return_documents", false);
        body.put("parameters", parameters);
        return body;
    }

    @Override
    protected String resolveEndpoint() {
        if (StringUtils.hasText(baseUrl)) {
            String normalized = baseUrl.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized + RERANK_PATH;
        }
        return DASHSCOPE_DEFAULT_BASE_URL + RERANK_PATH;
    }

    @Override
    protected JsonNode extractResultsNode(JsonNode root) {
        return root.path("output").path("results");
    }
}
