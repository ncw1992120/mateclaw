package vip.mate.dataagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import vip.mate.dataagent.dto.AloudataSearchResult.DimensionHit;
import vip.mate.dataagent.dto.AloudataSearchResult.MetricHit;

import java.util.List;

/**
 * 语义检索 Rerank 输出
 * <p>
 * 承载 rerank 精排后的指标/维度命中列表及执行状态，
 * 供检索链路按需替换原始排序结果。
 */
@Data
@AllArgsConstructor
public class SemanticRerankOutput {

    /** 重排后的指标命中列表 */
    private List<MetricHit> metricHits;

    /** 重排后的维度命中列表 */
    private List<DimensionHit> dimensionHits;

    /** 是否实际执行了 rerank（开关关闭 / 未配置 rerank 模型 / 调用失败时为 false） */
    private boolean reranked;

    /** 使用的 rerank 模型名称（未执行时为 null） */
    private String rerankModelName;
}
