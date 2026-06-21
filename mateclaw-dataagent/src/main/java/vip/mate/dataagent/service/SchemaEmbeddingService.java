package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;

/**
 * Schema 向量化检索服务接口
 * <p>
 * 提供数据源 Schema 的向量化嵌入和语义检索能力。
 * 将表级 Schema 信息（表名+表描述+列名+列描述+语义模型）嵌入为向量，
 * 使用 Elasticsearch 进行关键词检索和向量语义检索（混合检索 RRF 融合）。
 * Elasticsearch 不可用时自动降级为 MySQL LIKE 模糊匹配。
 */
public interface SchemaEmbeddingService {

    /**
     * 为数据源的所有表生成 Schema 嵌入
     * <p>
     * 遍历数据源下的所有表，为每张表构建嵌入文本并生成向量。
     * 已存在且版本一致的跳过，版本不一致的重新嵌入。
     *
     * @param datasourceId 数据源 ID
     * @return 新增或更新的嵌入数量
     */
    int embedSchema(Long datasourceId);

    /**
     * 为单张表生成 Schema 嵌入
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     * @return 是否成功
     */
    boolean embedTable(Long datasourceId, String tableName);

    /**
     * 语义检索相关表
     * <p>
     * 优先使用 Elasticsearch 进行关键词检索和向量语义检索（混合模式 RRF 融合），
     * ES 不可用时降级为 MySQL LIKE 模糊匹配 + 内存余弦相似度计算。
     * <ul>
     *   <li>关键词检索：ES multi_match / MySQL LIKE 模糊匹配</li>
     *   <li>向量语义检索：ES kNN 近似最近邻 / 内存余弦相似度</li>
     *   <li>混合检索：ES RRF 融合 / 内存 RRF 融合</li>
     * </ul>
     *
     * @param request 检索请求
     * @return 检索结果
     */
    SchemaSearchResult searchSchema(SchemaSearchRequest request);

    /**
     * 删除数据源的所有 Schema 嵌入
     *
     * @param datasourceId 数据源 ID
     */
    void deleteByDatasourceId(Long datasourceId);

    /**
     * 删除单张表的 Schema 嵌入
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     */
    void deleteByTableName(Long datasourceId, String tableName);

    /**
     * 构建表级 Schema 嵌入文本
     * <p>
     * 格式: "表名 表注释 | 字段: 列名1(业务别名1) [类型1] - 描述1, 列名2(业务别名2) [类型2] - 描述2, ..."
     *
     * @param datasourceId 数据源 ID
     * @param tableName    表名
     * @return 嵌入文本
     */
    String buildEmbeddingText(Long datasourceId, String tableName);
}
