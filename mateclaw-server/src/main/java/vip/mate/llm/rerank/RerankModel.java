package vip.mate.llm.rerank;

import java.util.List;

/**
 * Rerank 模型统一接口
 * <p>
 * Spring AI 未内置 RerankModel，此处自定义最小接口。
 * 实现类按 {@link vip.mate.llm.model.RerankProtocol} 分派：
 * DashScope（gte-rerank 系列）与 OpenAI 兼容协议（Cohere / Jina 等）。
 *
 * @author MateClaw Team
 */
public interface RerankModel {

    /**
     * 对 query 与 documents 执行相关性重排
     *
     * @param request 请求体（query + 候选文档 + 可选 topN）
     * @return 重排结果列表（按相关性降序）
     */
    List<RerankResult> rerank(RerankRequest request);
}
