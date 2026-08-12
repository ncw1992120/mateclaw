package vip.mate.dataagent.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.dataagent.aloudata.SemanticRerankProperties;
import vip.mate.dataagent.dto.AloudataSearchResult.DimensionHit;
import vip.mate.dataagent.dto.AloudataSearchResult.MetricHit;
import vip.mate.dataagent.dto.SemanticRerankOutput;
import vip.mate.dataagent.service.SemanticRerankService;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.rerank.RerankResult;
import vip.mate.sdk.service.MateClawRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 语义检索 Rerank 服务实现
 * <p>
 * 通过 MateClawRuntime 调用 server 层封装的 rerank 模型（经 SDK 透传），
 * 对 ES 检索命中的指标/维度候选按与用户查询的相关度二次精排。
 * 开关关闭、未配置默认 rerank 模型或调用失败时静默降级为原始排序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticRerankServiceImpl implements SemanticRerankService {

    private final MateClawRuntime mateClawRuntime;
    private final SemanticRerankProperties rerankProperties;

    @Override
    public SemanticRerankOutput rerankSemanticHits(List<MetricHit> metricHits,
                                                   List<DimensionHit> dimensionHits,
                                                   String query) {
        /* 开关未开启时不执行，保持原始排序 */
        if (!rerankProperties.isRerankEnabled()) {
            return new SemanticRerankOutput(metricHits, dimensionHits, false, null);
        }

        /* 未配置默认 rerank 模型时静默降级 */
        ModelConfigEntity rerankModel = mateClawRuntime.getDefaultRerankModel();
        if (rerankModel == null) {
            return new SemanticRerankOutput(metricHits, dimensionHits, false, null);
        }

        List<MetricHit> safeMetrics = metricHits == null ? Collections.emptyList() : metricHits;
        List<DimensionHit> safeDimensions = dimensionHits == null ? Collections.emptyList() : dimensionHits;
        if (safeMetrics.isEmpty() && safeDimensions.isEmpty()) {
            return new SemanticRerankOutput(metricHits, dimensionHits, false, null);
        }

        int metricCount = safeMetrics.size();
        try {
            /* 指标在前、维度在后拼装为统一候选文档列表，一次调用完成两组打分 */
            List<String> documents = new ArrayList<>(safeMetrics.size() + safeDimensions.size());
            for (MetricHit hit : safeMetrics) {
                documents.add(buildMetricDocument(hit));
            }
            for (DimensionHit hit : safeDimensions) {
                documents.add(buildDimensionDocument(hit));
            }

            List<RerankResult> results = mateClawRuntime.rerank(
                    rerankModel.getId(), query, documents, documents.size());

            /* 原始下标 -> rerank 分数 映射，未返回的候选保持原相对顺序 */
            Map<Integer, Double> scoreMap = new HashMap<>();
            for (RerankResult result : results) {
                scoreMap.put(result.getIndex(), result.getRelevanceScore());
            }

            List<MetricHit> rerankedMetrics = rerankOrder(safeMetrics, scoreMap, 0,
                    (hit, score) -> hit.setScore(score));
            List<DimensionHit> rerankedDimensions = rerankOrder(safeDimensions, scoreMap, metricCount,
                    (hit, score) -> hit.setScore(score));

            log.info("语义检索 rerank 完成: 模型={}, 指标 {} 条, 维度 {} 条",
                    rerankModel.getName(), rerankedMetrics.size(), rerankedDimensions.size());
            return new SemanticRerankOutput(rerankedMetrics, rerankedDimensions, true, rerankModel.getName());
        } catch (Exception e) {
            /* rerank 调用失败静默降级，返回原始排序 */
            log.warn("语义检索 rerank 执行失败，降级为原始排序: {}", e.getMessage());
            return new SemanticRerankOutput(metricHits, dimensionHits, false, null);
        }
    }

    /**
     * 按 rerank 分数对候选列表重排
     * <p>
     * 有分数的候选按分数降序排列并回写分数；未返回分数的候选
     * （API 截断或异常）保持原相对顺序追加在末尾，保证不丢数据。
     *
     * @param source      原始候选列表
     * @param scoreMap    原始下标 -> rerank 分数映射
     * @param offset      该组候选在统一文档列表中的起始下标
     * @param scoreSetter 分数回写回调（更新命中项分数，保持展示排序与分数一致）
     */
    private <T> List<T> rerankOrder(List<T> source, Map<Integer, Double> scoreMap, int offset,
                                    BiConsumer<T, Double> scoreSetter) {
        List<IndexedHit<T>> scoredEntries = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Double score = scoreMap.get(offset + i);
            if (score != null) {
                T hit = source.get(i);
                scoreSetter.accept(hit, score);
                scoredEntries.add(new IndexedHit<>(i, hit, score));
            }
        }
        scoredEntries.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<T> result = new ArrayList<>(source.size());
        Set<Integer> consumed = new HashSet<>();
        for (IndexedHit<T> entry : scoredEntries) {
            result.add(entry.hit());
            consumed.add(entry.index());
        }
        for (int i = 0; i < source.size(); i++) {
            if (!consumed.contains(i)) {
                result.add(source.get(i));
            }
        }
        return result;
    }

    /**
     * 拼装指标 rerank 文档（名称 + 展示名 + 口径 + 同义词 + 类型）
     */
    private String buildMetricDocument(MetricHit hit) {
        StringBuilder sb = new StringBuilder(hit.getMetricName());
        if (hit.getMetricDisplayName() != null && !hit.getMetricDisplayName().isBlank()) {
            sb.append("(").append(hit.getMetricDisplayName()).append(")");
        }
        if (hit.getBusinessCaliber() != null && !hit.getBusinessCaliber().isBlank()) {
            sb.append(" - ").append(hit.getBusinessCaliber());
        }
        if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
            sb.append(", 同义词: ").append(hit.getSynonyms());
        }
        if (hit.getType() != null && !hit.getType().isBlank()) {
            sb.append(", 类型: ").append(hit.getType());
        }
        return sb.toString();
    }

    /**
     * 拼装维度 rerank 文档（名称 + 展示名 + 描述 + 同义词）
     */
    private String buildDimensionDocument(DimensionHit hit) {
        StringBuilder sb = new StringBuilder(hit.getDimName());
        if (hit.getDimDisplayName() != null && !hit.getDimDisplayName().isBlank()) {
            sb.append("(").append(hit.getDimDisplayName()).append(")");
        }
        if (hit.getDimDescription() != null && !hit.getDimDescription().isBlank()) {
            sb.append(" - ").append(hit.getDimDescription());
        }
        if (hit.getSynonyms() != null && !hit.getSynonyms().isBlank()) {
            sb.append(", 同义词: ").append(hit.getSynonyms());
        }
        return sb.toString();
    }

    /**
     * 带原始下标的命中项包装，用于重排时保留原始位置信息
     */
    private record IndexedHit<T>(int index, T hit, double score) {
    }
}
