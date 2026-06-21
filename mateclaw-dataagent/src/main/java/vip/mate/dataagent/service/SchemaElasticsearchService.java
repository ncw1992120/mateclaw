package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;

import java.util.List;

/**
 * Schema Elasticsearch 检索服务接口
 * <p>
 * 基于 Elasticsearch 实现关键词检索和向量语义检索，
 * 替代原有的 MySQL LIKE 模糊匹配和内存余弦相似度计算。
 */
public interface SchemaElasticsearchService {

    /**
     * 确保索引存在，若不存在则自动创建
     *
     * @param vectorDimension 向量维度（用于配置 dense_vector 字段）
     */
    void ensureIndex(int vectorDimension);

    /**
     * 索引单张表的 Schema 嵌入文档
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     * @param embeddingText 嵌入文本
     * @param embedding    向量数据（可为 null，表示仅索引文本）
     */
    void indexSchema(Long datasourceId, String tableName, String embeddingText, float[] embedding);

    /**
     * 删除数据源的所有 Schema 嵌入文档
     *
     * @param datasourceId 数据源 ID
     */
    void deleteByDatasourceId(Long datasourceId);

    /**
     * 删除单张表的 Schema 嵌入文档
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     */
    void deleteByTableName(Long datasourceId, String tableName);

    /**
     * 关键词检索
     * <p>
     * 使用 Elasticsearch multi_match 查询在嵌入文本中匹配关键词
     *
     * @param datasourceId 数据源 ID
     * @param query        查询关键词
     * @param topK         返回结果数量上限
     * @return 命中表名及其分数
     */
    List<SchemaSearchResult.TableHit> keywordSearch(Long datasourceId, String query, int topK);

    /**
     * 向量语义检索
     * <p>
     * 使用 Elasticsearch kNN 搜索进行近似最近邻检索
     *
     * @param datasourceId       数据源 ID
     * @param queryVector        查询向量
     * @param topK               返回结果数量上限
     * @param similarityThreshold 相似度阈值
     * @return 命中表名及其分数
     */
    List<SchemaSearchResult.TableHit> semanticSearch(Long datasourceId, float[] queryVector,
                                                      int topK, double similarityThreshold);

    /**
     * 混合检索（关键词 + 向量语义）
     * <p>
     * 同时执行关键词检索和向量语义检索，使用 RRF 融合结果
     *
     * @param request             检索请求
     * @param queryVector         查询向量（可为 null，表示仅关键词检索）
     * @return 检索结果
     */
    SchemaSearchResult hybridSearch(SchemaSearchRequest request, float[] queryVector);
}
