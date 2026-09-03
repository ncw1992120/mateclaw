package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.AloudataSearchResult;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;

import java.util.List;

/**
 * Aloudata 语义层 Elasticsearch 检索服务接口
 * <p>
 * 基于 Elasticsearch 实现指标级和维度级的关键词检索与向量语义检索，
 * 支持混合检索（RRF 融合）。ES 不可用时降级为 MySQL LIKE 查询。
 */
public interface AloudataSemanticEsService {

    /**
     * 确保指标和维度索引存在，若不存在则自动创建
     *
     * @param vectorDimension 向量维度（用于配置 dense_vector 字段）
     */
    void ensureIndices(int vectorDimension);

    /**
     * 索引指标文档
     *
     * @param entity 指标实体（含 embeddingText 和 embedding）
     */
    void indexMetric(AloudataMetricEntity entity);

    /**
     * 批量索引指标文档
     *
     * @param entities 指标实体列表
     */
    void indexMetrics(List<AloudataMetricEntity> entities);

    /**
     * 索引维度文档
     *
     * @param entity 维度实体
     */
    void indexDimension(AloudataDimensionEntity entity);

    /**
     * 批量索引维度文档
     *
     * @param entities 维度实体列表
     */
    void indexDimensions(List<AloudataDimensionEntity> entities);

    /**
     * 删除数据源的所有指标和维度文档
     *
     * @param datasourceId 数据源 ID
     */
    void deleteByDatasourceId(Long datasourceId);

    /**
     * 混合检索指标和维度
     * <p>
     * 同时执行关键词检索和向量语义检索，使用 RRF 融合结果。
     * ES 不可用时降级为 MySQL LIKE 查询。
     *
     * @param datasourceId       数据源 ID
     * @param query              搜索关键词
     * @param topK               返回结果数量上限
     * @param similarityThreshold 向量语义检索相似度阈值
     * @return 检索结果（含指标命中和维度命中）
     */
    AloudataSearchResult hybridSearch(Long datasourceId, String query, int topK, double similarityThreshold);

    /**
     * 多关键词合并混合检索指标和维度
     * <p>
     * 将多个扩展关键词合并为单个查询（空格分隔），在同一个 RRF 分值空间内融合，
     * 避免逐词检索后跨查询分数不可比的问题。
     *
     * @param datasourceId       数据源 ID
     * @param keywords           搜索关键词列表（第一个为原始关键词，后续为扩展词）
     * @param topK               返回结果数量上限
     * @param similarityThreshold 向量语义检索相似度阈值
     * @return 检索结果（含指标命中和维度命中）
     */
    AloudataSearchResult hybridSearchMerged(Long datasourceId, List<String> keywords, int topK, double similarityThreshold);

    /**
     * 增强混合检索：在多关键词合并检索基础上，引入用户原话作为并行检索路径。
     * <p>
     * 相比 {@link #hybridSearchMerged}，本方法额外利用用户原始消息：
     * <ul>
     *   <li>向量路：对原始关键词和用户原话分别生成向量，kNN 结果取并集（max score 去重），
     *       解决 LLM 压缩 keyword 后与指标展示名 embedding 空间不一致的问题</li>
     *   <li>维度路：用户原话通常包含维度上下文（如"各区域"），用原话作为维度 kNN 的主向量，
     *       缓解指标/维度共用 keyword 导致的语义污染</li>
     * </ul>
     * originalMessage 为 null 或与首关键词相同时，退化为 {@link #hybridSearchMerged} 行为。
     *
     * @param datasourceId       数据源 ID
     * @param keywords           搜索关键词列表（第一个为原始关键词，后续为扩展词）
     * @param originalMessage    用户原始消息（可为 null）
     * @param topK               返回结果数量上限
     * @param similarityThreshold 向量语义检索相似度阈值
     * @return 检索结果（含指标命中和维度命中）
     */
    AloudataSearchResult hybridSearchEnhanced(Long datasourceId, List<String> keywords,
                                              String originalMessage, int topK, double similarityThreshold);

    /**
     * 为尚未补充相关维度的指标命中项（族级兜底补入成员）批量补充
     * availableDimensions 与 relevantDimensions，与检索时 enrich 使用同一套相关性打分。
     * <p>
     * 仅处理 relevantDimensions == null 的命中项（待补标记），已补充的跳过。
     *
     * @param hits            指标命中列表
     * @param datasourceId    数据源 ID
     * @param keywords        搜索关键词列表（第一个为原始关键词，后续为扩展词，可只传单个）
     * @param originalMessage 用户原始消息（可为 null）
     */
    void enrichMissingDimensions(List<AloudataSearchResult.MetricHit> hits, Long datasourceId,
                                List<String> keywords, String originalMessage);
}
