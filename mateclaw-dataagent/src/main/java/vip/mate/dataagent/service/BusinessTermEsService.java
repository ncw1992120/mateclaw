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
     * 混合检索术语
     * <p>
     * 同时执行关键词检索和向量语义检索，使用 RRF 融合结果。
     * ES 不可用时降级为 MySQL LIKE 查询。
     *
     * @param tenantCode         租户编码
     * @param query              搜索关键词
     * @param topK               返回结果数量上限
     * @param similarityThreshold 向量语义检索相似度阈值
     * @return 检索结果
     */
    BusinessTermSearchResult hybridSearch(String tenantCode, String query, int topK, double similarityThreshold);
}
