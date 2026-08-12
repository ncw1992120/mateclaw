package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.AloudataSearchResult.DimensionHit;
import vip.mate.dataagent.dto.AloudataSearchResult.MetricHit;
import vip.mate.dataagent.dto.SemanticRerankOutput;

import java.util.List;

/**
 * 语义检索 Rerank 服务接口
 * <p>
 * 在 ES 双路检索 + RRF 融合 + TopK 截断之后，使用 rerank 模型
 * 对候选命中项按与用户查询的相关度做二次精排。
 * 是否开启由系统配置 {@code dataagent.search.rerank.enabled} 控制。
 */
public interface SemanticRerankService {

    /**
     * 对语义检索命中项执行 rerank 精排
     * <p>
     * 指标与维度合并为统一候选文档列表，一次调用 rerank 模型打分后
     * 分别按分数重排两组命中（指标在前、维度在后的原始分组不改变）。
     * 任何异常（开关关闭、无默认 rerank 模型、API 调用失败）均静默降级，
     * 返回原始排序且 reranked=false。
     *
     * @param metricHits     指标命中列表（可为 null）
     * @param dimensionHits  维度命中列表（可为 null）
     * @param query          用户查询（用于 rerank 相关性打分）
     * @return rerank 输出（含重排后的列表与执行状态）
     */
    SemanticRerankOutput rerankSemanticHits(List<MetricHit> metricHits,
                                            List<DimensionHit> dimensionHits,
                                            String query);
}
