package vip.mate.llm.model;

/**
 * Rerank 模型协议。
 * <p>
 * 与 {@link ModelProtocol}（Chat 协议）和 {@link EmbeddingProtocol}（向量协议）分离——
 * rerank 在同一个 Provider 下可能走不同的请求格式：
 * <ul>
 *   <li>DashScope 的 rerank endpoint 是专用 path
 *       （/api/v1/services/rerank/text-rerank/text-rerank，模型如 gte-rerank 系列）</li>
 *   <li>OpenAI 兼容协议的 rerank 统一走 {baseUrl}/rerank（Cohere / Jina 等）</li>
 * </ul>
 * <p>
 * Routing happens in {@code RerankModelFactory.resolveRerankProtocol}
 * via the {@code chatModel} column on {@link ModelProviderEntity}，
 * 与 Embedding 的协议解析保持同一信号源。
 *
 * @author MateClaw Team
 */
public enum RerankProtocol {

    /** DashScope 原生 rerank 端点 */
    DASHSCOPE_RERANK("dashscope-rerank"),

    /** OpenAI 兼容协议 rerank（Cohere / Jina 等） */
    OPENAI_RERANK("openai-rerank");

    private final String id;

    RerankProtocol(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
