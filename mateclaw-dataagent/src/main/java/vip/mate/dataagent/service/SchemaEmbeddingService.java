package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.SchemaSearchRequest;
import vip.mate.dataagent.dto.SchemaSearchResult;

/**
 * Schema 向量化检索服务接口
 * <p>
 * 提供数据源 Schema 的向量化嵌入和语义检索能力。
 * 将表级 Schema 信息（表名+表描述+列名+列描述+语义模型）嵌入为向量，
 * 支持关键词检索、向量语义检索和混合检索（RRF 融合）。
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
     * 支持三种检索模式：
     * <ul>
     *   <li>关键词检索：在表名、列名、业务名、描述等字段中匹配</li>
     *   <li>向量语义检索：基于 Schema 嵌入向量的余弦相似度</li>
     *   <li>混合检索：RRF 融合关键词和语义检索结果</li>
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
