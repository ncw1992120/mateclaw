package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.BusinessTermSearchResult;
import vip.mate.dataagent.model.BusinessTermEntity;

import java.util.List;

/**
 * 业务术语 Elasticsearch 检索服务接口
 * <p>
 * 基于 Elasticsearch 实现术语的关键词检索与向量语义检索，
 * 支持混合检索（RRF 融合）。ES 不可用时降级为 MySQL LIKE 查询。
 */
public interface BusinessTermEsService {

    /**
     * 确保术语索引存在，若不存在则自动创建
     *
     * @param vectorDimension 向量维度（用于配置 dense_vector 字段）
     */
    void ensureIndex(int vectorDimension);

    /**
     * 索引术语文档
     *
     * @param entity 术语实体（含 embeddingText 和 embedding）
     */
    void indexTerm(BusinessTermEntity entity);

    /**
     * 批量索引术语文档
     *
     * @param entities 术语实体列表
     */
    void indexTerms(List<BusinessTermEntity> entities);

    /**
     * 删除租户的所有术语文档
     *
     * @param tenantCode 租户编码
     */
    void deleteByTenantCode(String tenantCode);

    /**
     * 删除指定术语文档
     *
     * @param entity 术语实体
     */
    void deleteTerm(BusinessTermEntity entity);

    /**
     * 混合检索术语（跨所有租户）
     * <p>
     * 同时执行关键词检索和向量语义检索，使用 RRF 融合结果。
     * ES 不可用时降级为 MySQL LIKE 查询。
     *
     * @param query              搜索关键词
     * @param topK               返回结果数量上限
     * @param similarityThreshold 向量语义检索相似度阈值
     * @return 检索结果
     */
    BusinessTermSearchResult hybridSearch(String query, int topK, double similarityThreshold);

    /**
     * 术语精确命中检索（确定性，不走 ES 打分 / 向量 / topK 截断）。
     * <p>
     * 关键词与某术语的 termName 或任一同义词在去除标点/空白/全角半角差异后**完全相等**时判定命中，
     * 覆盖 LLM 格式化（加空格/改标点）与用户直接使用缩写/同义词（如「GMV」命中术语「成交总额」的同义词）的场景。
     * 走元数据表精确查询，不依赖 ES 打分与 topK；同一归一化词理论上可能对应多个术语（0..N 个命中），
     * 返回的 {@link BusinessTermSearchResult.TermHit#getMatchSource()} 为 {@code exact}。
     *
     * @param keyword    搜索关键词（可为 null/空白，此时返回空列表）
     * @param tenantCode 租户编码，null 表示跨所有租户
     * @return 精确命中术语列表，无命中或检索异常时为空列表
     */
    List<BusinessTermSearchResult.TermHit> exactSearch(String keyword, String tenantCode);
}
