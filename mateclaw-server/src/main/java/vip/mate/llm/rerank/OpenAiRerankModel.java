package vip.mate.llm.rerank;

import com.fasterxml.jackson.databind.ObjectMapper;
import vip.mate.exception.MateClawException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 兼容协议 Rerank 模型实现
 * <p>
 * 适用于所有提供 {@code POST {baseUrl}/rerank} 的服务：
 * <ul>
 *   <li>Cohere / Jina：baseUrl 填官方根地址（Cohere 无 /v1，Jina 需带 /v1）</li>
 *   <li>本地部署 bge-reranker 系列：Xinference（/v1/rerank）、vLLM（/v1/rerank）、
 *       TeAI / FlagEmbedding 官方服务（/rerank），baseUrl 按实际端点带/不带 /v1</li>
 * </ul>
 * 请求体为 {@code {"model","query","documents","top_n"}} 的平铺结构。
 *
 * @author MateClaw Team
 */
public class OpenAiRerankModel extends AbstractRerankModel {

    /** rerank 端点路径后缀 */
    private static final String RERANK_PATH = "/rerank";

    /**
     * 构造 OpenAI 兼容 Rerank 模型
     *
     * @param apiKey       API Key
     * @param baseUrl      API 基础地址（必须非空）
     * @param modelName    模型名
     * @param objectMapper Jackson 序列化器
     */
    public OpenAiRerankModel(String apiKey, String baseUrl, String modelName, ObjectMapper objectMapper) {
        super(apiKey, baseUrl, modelName, objectMapper);
    }

    @Override
    protected Map<String, Object> buildRequestBody(RerankRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("query", request.getQuery());
        body.put("documents", request.getDocuments());
        if (request.getTopN() != null && request.getTopN() > 0) {
            body.put("top_n", request.getTopN());
        }
        return body;
    }

    @Override
    protected String resolveEndpoint() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new MateClawException("err.rerank.base_url_missing",
                    "OpenAI 兼容 Rerank 需要配置 Base URL");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + RERANK_PATH;
    }
}
