package vip.mate.dataagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.dataagent.constants.DataAgentConstants;
import vip.mate.dataagent.dto.AloudataSearchResult;
import vip.mate.dataagent.dto.AloudataSearchResult.DimensionHit;
import vip.mate.dataagent.dto.AloudataSearchResult.MetricHit;
import vip.mate.dataagent.model.AloudataDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricDimensionEntity;
import vip.mate.dataagent.model.AloudataMetricEntity;
import vip.mate.dataagent.repository.AloudataDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricDimensionMapper;
import vip.mate.dataagent.repository.AloudataMetricMapper;
import vip.mate.dataagent.service.AloudataSemanticEsService;
import vip.mate.llm.embedding.EmbeddingModelFactory;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.wiki.service.WikiEmbeddingService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aloudata 语义层 Elasticsearch 检索服务实现
 * <p>
 * 基于 Elasticsearch 8.x Java Client 实现指标级和维度级的关键词检索（multi_match）、向量语义检索（kNN）和混合检索（RRF 融合）。
 * ES 不可用时优雅降级为 MySQL LIKE 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AloudataSemanticEsServiceImpl implements AloudataSemanticEsService {

    private final AloudataMetricMapper metricMapper;
    private final AloudataDimensionMapper dimensionMapper;
    private final AloudataMetricDimensionMapper metricDimensionMapper;
    private final ModelConfigService modelConfigService;

    @Autowired(required = false)
    private ElasticsearchClient esClient;

    /** 可选依赖：Embedding 模型工厂，缺失时向量检索降级 */
    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    /** 向量维度缓存 */
    private volatile int cachedVectorDimension = -1;

    /** 索引是否已初始化 */
    private volatile boolean indicesInitialized = false;

    /**
     * 获取可用的 ES 客户端
     */
    private ElasticsearchClient getAvailableClient() {
        if (esClient == null) {
            return null;
        }
        try {
            esClient.ping();
            return esClient;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void ensureIndices(int vectorDimension) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.error("Elasticsearch 客户端不可用，跳过索引创建");
            return;
        }

        ensureMetricIndex(client, vectorDimension);
        ensureDimensionIndex(client, vectorDimension);

        cachedVectorDimension = vectorDimension;
        indicesInitialized = true;
    }

    @Override
    public void indexMetric(AloudataMetricEntity entity) {
        indexMetrics(List.of(entity));
    }

    @Override
    public void indexMetrics(List<AloudataMetricEntity> entities) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null || entities == null || entities.isEmpty()) {
            return;
        }

        ensureIndicesIfNeeded(entities.get(0));

        String indexName = DataAgentConstants.ALOUDATA_METRIC_ES_INDEX;
        int batchSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;

        /* 分批使用 Bulk API 写入 */
        for (int i = 0; i < entities.size(); i += batchSize) {
            List<AloudataMetricEntity> batch = entities.subList(i, Math.min(i + batchSize, entities.size()));
            try {
                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
                for (AloudataMetricEntity entity : batch) {
                    String docId = entity.getDatasourceId() + "_" + entity.getMetricName();
                    Map<String, Object> doc = buildMetricDocument(entity);
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .id(docId)
                                    .document(doc)
                            )
                    );
                }
                BulkResponse bulkResponse = client.bulk(bulkBuilder.build());
                if (bulkResponse.errors()) {
                    long failCount = bulkResponse.items().stream().filter(item -> item.error() != null).count();
                    log.error("ES Bulk 写入指标部分失败，失败数: {}", failCount);
                }
            } catch (IOException e) {
                log.error("ES Bulk 写入指标失败 (batch {}): {}", i / batchSize, e.getMessage());
            }
        }
        log.info("ES Bulk 索引写入指标完成，数量: {}", entities.size());
    }

    @Override
    public void indexDimension(AloudataDimensionEntity entity) {
        indexDimensions(List.of(entity));
    }

    @Override
    public void indexDimensions(List<AloudataDimensionEntity> entities) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null || entities == null || entities.isEmpty()) {
            return;
        }

        ensureIndicesIfNeeded(entities.get(0));

        String indexName = DataAgentConstants.ALOUDATA_DIMENSION_ES_INDEX;
        int batchSize = DataAgentConstants.ALOUDATA_SYNC_BATCH_UPSERT_SIZE;

        /* 分批使用 Bulk API 写入 */
        for (int i = 0; i < entities.size(); i += batchSize) {
            List<AloudataDimensionEntity> batch = entities.subList(i, Math.min(i + batchSize, entities.size()));
            try {
                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
                for (AloudataDimensionEntity entity : batch) {
                    String docId = entity.getDatasourceId() + "_" + entity.getDimName();
                    Map<String, Object> doc = buildDimensionDocument(entity);
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .id(docId)
                                    .document(doc)
                            )
                    );
                }
                BulkResponse bulkResponse = client.bulk(bulkBuilder.build());
                if (bulkResponse.errors()) {
                    long failCount = bulkResponse.items().stream().filter(item -> item.error() != null).count();
                    log.error("ES Bulk 写入维度部分失败，失败数: {}", failCount);
                }
            } catch (IOException e) {
                log.error("ES Bulk 写入维度失败 (batch {}): {}", i / batchSize, e.getMessage());
            }
        }
        log.info("ES Bulk 索引写入维度完成，数量: {}", entities.size());
    }

    @Override
    public void deleteByDatasourceId(Long datasourceId) {
        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            return;
        }

        try {
            client.deleteByQuery(d -> d
                    .index(DataAgentConstants.ALOUDATA_METRIC_ES_INDEX)
                    .query(q -> q.term(t -> t.field("datasourceId").value(datasourceId)))
            );
            client.deleteByQuery(d -> d
                    .index(DataAgentConstants.ALOUDATA_DIMENSION_ES_INDEX)
                    .query(q -> q.term(t -> t.field("datasourceId").value(datasourceId)))
            );
            log.info("ES 索引删除完成，数据源: {}", datasourceId);
        } catch (IOException e) {
            log.error("ES 索引删除失败，数据源: {} - {}", datasourceId, e.getMessage());
        }
    }

    @Override
    public AloudataSearchResult hybridSearch(Long datasourceId, String query, int topK, double similarityThreshold) {
        long startTime = System.currentTimeMillis();
        AloudataSearchResult result = new AloudataSearchResult();
        result.setQuery(query);
        result.setDatasourceId(datasourceId);

        if (datasourceId == null || !StringUtils.hasText(query)) {
            result.setMetricHits(List.of());
            result.setDimensionHits(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            // 降级为 MySQL LIKE 查询
            log.error("ES 不可用，降级为 MySQL LIKE 查询");
            return fallbackMySqlSearch(datasourceId, List.of(query), topK, startTime);
        }

        ensureIndicesIfNeeded(null);

        // 单关键词检索：无扩展词，BM25 与向量使用同一查询串
        List<MetricHit> metricHits = esSearchMetrics(client, datasourceId, query, List.of(), topK, similarityThreshold);
        List<DimensionHit> dimensionHits = esSearchDimensions(client, datasourceId, query, List.of(), topK, similarityThreshold);

        // 为指标补充可用维度列表
        enrichMetricDimensions(metricHits, datasourceId);

        result.setMetricHits(metricHits);
        result.setDimensionHits(dimensionHits);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    @Override
    public AloudataSearchResult hybridSearchMerged(Long datasourceId, List<String> keywords, int topK, double similarityThreshold) {
        long startTime = System.currentTimeMillis();
        AloudataSearchResult result = new AloudataSearchResult();
        // 原始关键词（keywords 首个元素），用于向量路和精确匹配
        String primaryQuery = keywords == null || keywords.isEmpty() ? "" : keywords.getFirst();
        // 扩展词列表（不含原始关键词），用于 BM25 should 子句拓宽召回
        List<String> expandedWords = keywords == null || keywords.size() <= 1
                ? List.of() : keywords.subList(1, keywords.size());
        result.setQuery(primaryQuery + (expandedWords.isEmpty() ? "" : " (扩展: " + String.join(", ", expandedWords) + ")"));
        result.setDatasourceId(datasourceId);

        if (datasourceId == null || !StringUtils.hasText(primaryQuery)) {
            result.setMetricHits(List.of());
            result.setDimensionHits(List.of());
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.error("ES 不可用，降级为 MySQL LIKE 查询");
            return fallbackMySqlSearch(datasourceId, keywords, topK, startTime);
        }

        ensureIndicesIfNeeded(null);

        List<MetricHit> metricHits = esSearchMetrics(client, datasourceId, primaryQuery, expandedWords, topK, similarityThreshold);
        List<DimensionHit> dimensionHits = esSearchDimensions(client, datasourceId, primaryQuery, expandedWords, topK, similarityThreshold);

        // 为指标补充可用维度列表
        enrichMetricDimensions(metricHits, datasourceId);

        result.setMetricHits(metricHits);
        result.setDimensionHits(dimensionHits);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== ES 索引创建 ====================

    private void ensureMetricIndex(ElasticsearchClient client, int vectorDimension) {
        String indexName = DataAgentConstants.ALOUDATA_METRIC_ES_INDEX;
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("datasourceId", p -> p.long_(lo -> lo))
                            .properties("metricName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("metricDisplayName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("metricCode", p -> p.keyword(k -> k))
                            .properties("type", p -> p.keyword(k -> k))
                            .properties("businessCaliber", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("synonyms", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("categoryName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("unit", p -> p.keyword(k -> k))
                            .properties("cnUnit", p -> p.keyword(k -> k))
                            .properties("owner", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.info("Elasticsearch 指标索引 [{}] 创建成功，向量维度: {}", indexName, dims);
        } catch (Exception e) {
            log.error("创建指标索引失败(ik)，尝试标准分词器: {}", e.getMessage());
            tryCreateMetricIndexWithStandardAnalyzer(client, indexName, vectorDimension);
        }
    }

    private void tryCreateMetricIndexWithStandardAnalyzer(ElasticsearchClient client, String indexName, int vectorDimension) {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("datasourceId", p -> p.long_(lo -> lo))
                            .properties("metricName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("metricDisplayName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("metricCode", p -> p.keyword(k -> k))
                            .properties("type", p -> p.keyword(k -> k))
                            .properties("businessCaliber", p -> p.text(t -> t))
                            .properties("synonyms", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("categoryName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("unit", p -> p.keyword(k -> k))
                            .properties("cnUnit", p -> p.keyword(k -> k))
                            .properties("owner", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.warn("Elasticsearch 指标索引 [{}] 使用【标准分词器】降级创建（IK 不可用），中文检索按单字切分，"
                    + "精度会明显下降；请确认 ES 已安装 analysis-ik 插件后重建索引。向量维度: {}", indexName, dims);
        } catch (Exception ex) {
            log.error("Elasticsearch 指标索引创建失败（标准分词器降级）: {}", ex.getMessage(), ex);
        }
    }

    private void ensureDimensionIndex(ElasticsearchClient client, int vectorDimension) {
        String indexName = DataAgentConstants.ALOUDATA_DIMENSION_ES_INDEX;
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("datasourceId", p -> p.long_(lo -> lo))
                            .properties("dimName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("dimDisplayName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("dimCode", p -> p.keyword(k -> k))
                            .properties("dimDescription", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("synonyms", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))
                                    .fields("ikmax", f -> f.text(tt -> tt.analyzer("ik_max_word").searchAnalyzer("ik_max_word")))))
                            .properties("originDataType", p -> p.keyword(k -> k))
                            .properties("isTimeDimension", p -> p.boolean_(b -> b))
                            .properties("categoryName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("datasetName", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("exampleValues", p -> p.keyword(k -> k))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.info("Elasticsearch 维度索引 [{}] 创建成功，向量维度: {}", indexName, dims);
        } catch (Exception e) {
            log.error("创建维度索引失败(ik)，尝试标准分词器: {}", e.getMessage());
            tryCreateDimensionIndexWithStandardAnalyzer(client, indexName, vectorDimension);
        }
    }

    private void tryCreateDimensionIndexWithStandardAnalyzer(ElasticsearchClient client, String indexName, int vectorDimension) {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
            if (exists) {
                return;
            }

            final int dims = vectorDimension;
            client.indices().create(CreateIndexRequest.of(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("datasourceId", p -> p.long_(lo -> lo))
                            .properties("dimName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("dimDisplayName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("dimCode", p -> p.keyword(k -> k))
                            .properties("dimDescription", p -> p.text(t -> t))
                            .properties("synonyms", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("originDataType", p -> p.keyword(k -> k))
                            .properties("isTimeDimension", p -> p.boolean_(b -> b))
                            .properties("categoryName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("datasetName", p -> p.text(t -> t
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("exampleValues", p -> p.keyword(k -> k))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, p -> p.text(t -> t))
                            .properties(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, p -> p
                                    .denseVector(dv -> dv.dims(dims).index(true).similarity(DenseVectorSimilarity.Cosine)))
                    )
            ));
            log.warn("Elasticsearch 维度索引 [{}] 使用【标准分词器】降级创建（IK 不可用），中文检索按单字切分，"
                    + "精度会明显下降；请确认 ES 已安装 analysis-ik 插件后重建索引。向量维度: {}", indexName, dims);
        } catch (Exception ex) {
            log.error("Elasticsearch 维度索引创建失败（标准分词器降级）: {}", ex.getMessage(), ex);
        }
    }

    // ==================== ES 文档构建 ====================

    private Map<String, Object> buildMetricDocument(AloudataMetricEntity entity) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("datasourceId", entity.getDatasourceId());
        doc.put("metricName", entity.getMetricName());
        doc.put("metricDisplayName", entity.getMetricDisplayName());
        doc.put("metricCode", entity.getMetricCode());
        doc.put("type", entity.getType());
        doc.put("businessCaliber", entity.getBusinessCaliber());
        doc.put("synonyms", splitToList(entity.getSynonyms()));
        doc.put("categoryName", entity.getMetricCategoryName());
        doc.put("unit", entity.getUnit());
        doc.put("cnUnit", entity.getCnUnit());
        doc.put("owner", entity.getOwner());
        doc.put(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, entity.getEmbeddingText());
        if (entity.getEmbedding() != null && entity.getEmbedding().length > 0) {
            doc.put(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, bytesToFloatList(entity.getEmbedding()));
        }
        return doc;
    }

    private Map<String, Object> buildDimensionDocument(AloudataDimensionEntity entity) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("datasourceId", entity.getDatasourceId());
        doc.put("dimName", entity.getDimName());
        doc.put("dimDisplayName", entity.getDimDisplayName());
        doc.put("dimCode", entity.getDimCode());
        doc.put("dimDescription", entity.getDimDescription());
        doc.put("synonyms", splitToList(entity.getSynonyms()));
        doc.put("originDataType", entity.getOriginDataType());
        doc.put("isTimeDimension", Boolean.TRUE.equals(entity.getIsTimeDimension()));
        doc.put("categoryName", entity.getDimCategoryName());
        doc.put("datasetName", entity.getDatasetName());
        doc.put("exampleValues", splitToList(entity.getExampleValues()));
        doc.put(DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD, entity.getEmbeddingText());
        if (entity.getEmbedding() != null && entity.getEmbedding().length > 0) {
            doc.put(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD, bytesToFloatList(entity.getEmbedding()));
        }
        return doc;
    }

    // ==================== ES 混合检索 ====================

    private List<MetricHit> esSearchMetrics(ElasticsearchClient client, Long datasourceId,
                                            String primaryQuery, List<String> expandedWords, int topK, double threshold) {
        String indexName = DataAgentConstants.ALOUDATA_METRIC_ES_INDEX;
        // 每路先放大候选池，融合/排序后再由调用方截断到 topK
        int pool = retrievalPoolSize(topK);
        Query keywordQuery = buildMetricKeywordQuery(datasourceId, primaryQuery, expandedWords);
        try {
            // 判断是否需要向量检索（向量只用原始关键词，避免扩展词稀释语义）
            boolean hasVector = hasMetricEmbeddings(datasourceId);
            List<Float> queryVector = hasVector ? getQueryVector(datasourceId, primaryQuery) : List.of();
            boolean canKnn = !queryVector.isEmpty();

            if (canKnn) {
                // 混合检索：分别执行关键词查询和 kNN 查询，应用层 RRF 融合
                // （ES 内置 RRF 需要 Platinum 许可证，Basic 许可证不支持）

                // 1. 关键词查询（BM25，原始关键词 must + 扩展词 should OR 拓宽召回）
                SearchResponse<Map> keywordResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );

                // 2. kNN 向量查询（只用原始关键词的向量）
                SearchResponse<Map> knnResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .knn(knn -> knn
                                        .field(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD)
                                        .queryVector(queryVector)
                                        .k(pool)
                                        .numCandidates(Math.max(DataAgentConstants.ES_KNN_NUM_CANDIDATES, pool))
                                        .filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)))),
                        Map.class
                );

                // 3. 应用层 RRF 融合
                return rrfMergeMetricHits(keywordResponse, knnResponse);
            } else {
                // 仅关键词检索（带字段权重）
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );
                return extractMetricHits(response, "keyword", 0);
            }
        } catch (Exception e) {
            log.error("ES 指标检索失败，降级为 MySQL: {}", e.getMessage());
            List<String> allKeywords = new ArrayList<>();
            allKeywords.add(primaryQuery);
            if (expandedWords != null) {
                allKeywords.addAll(expandedWords);
            }
            return fallbackMySqlSearchMetrics(datasourceId, allKeywords, topK);
        }
    }

    /**
     * 构建指标关键词查询。
     * <p>
     * 查询结构：
     * <ul>
     *   <li>filter: datasourceId 精确过滤</li>
     *   <li>must: 原始关键词的 multiMatch（BestFields + tieBreaker），保证核心召回</li>
     *   <li>should: 扩展词的 multiMatch（BestFields），OR 语义拓宽召回，命中任一即加分</li>
     *   <li>should: .keyword 子字段精确匹配提权，命中加分、不命中不影响召回</li>
     * </ul>
     * <p>
     * 关键设计：扩展词用 should 而非拼入 must，避免 CrossFields/BestFields 多词变 AND
     * 导致"扩展越多召回越差"的问题。should 子句命中任一即可为文档加分，不命中也不影响
     * 原始关键词的 must 召回。
     */
    private Query buildMetricKeywordQuery(Long datasourceId, String primaryQuery, List<String> expandedWords) {
        return Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)));
            // 原始关键词 must：BestFields 取最高分字段，tieBreaker 让次高分字段也贡献部分分数
            b.must(m -> m.multiMatch(mm -> mm
                    .fields("metricName^3", "metricName.ikmax^2", "metricDisplayName^2",
                            "metricDisplayName.ikmax^1", "synonyms^2", "synonyms.ikmax^1",
                            "businessCaliber^1", "categoryName^1",
                            DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^1")
                    .type(TextQueryType.BestFields)
                    .tieBreaker(0.3)
                    .query(primaryQuery)));
            // 扩展词 should：每个扩展词独立 multiMatch，OR 语义，命中任一即加分
            if (expandedWords != null) {
                for (String expanded : expandedWords) {
                    if (StringUtils.hasText(expanded) && !expanded.equals(primaryQuery)) {
                        b.should(sh -> sh.multiMatch(mm -> mm
                                .fields("metricName^2", "metricDisplayName^2", "synonyms^2",
                                        "businessCaliber^1", "categoryName^1",
                                        DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^0.5")
                                .type(TextQueryType.BestFields)
                                .tieBreaker(0.3)
                                .query(expanded)));
                    }
                }
            }
            // .keyword 精确匹配提权：命中加分，不命中不影响 must 召回
            b.should(sh -> sh.term(t -> t.field("metricName.keyword").value(primaryQuery).boost(5.0f)));
            b.should(sh -> sh.term(t -> t.field("metricDisplayName.keyword").value(primaryQuery).boost(3.0f)));
            b.should(sh -> sh.term(t -> t.field("synonyms.keyword").value(primaryQuery).boost(2.0f)));
            return b;
        }));
    }

    /**
     * 计算每路（关键词 / 向量）在 RRF 融合前的候选召回数量。
     * <p>
     * 混合检索必须先各路放大召回、融合后再截断到 topK；否则两路都排在 topK 之外的
     * 真实命中会永远进不了 RRF，表现为"数据存在却检索不出"。
     */
    private int retrievalPoolSize(int topK) {
        int base = Math.max(topK, 1);
        return Math.min(base * DataAgentConstants.ALOUDATA_SEARCH_RETRIEVAL_POOL_FACTOR,
                DataAgentConstants.ALOUDATA_SEARCH_MAX_RETRIEVAL_POOL);
    }

    private List<DimensionHit> esSearchDimensions(ElasticsearchClient client, Long datasourceId,
                                                  String primaryQuery, List<String> expandedWords, int topK, double threshold) {
        String indexName = DataAgentConstants.ALOUDATA_DIMENSION_ES_INDEX;
        // 每路先放大候选池，融合/排序后再由调用方截断到 topK
        int pool = retrievalPoolSize(topK);
        Query keywordQuery = buildDimensionKeywordQuery(datasourceId, primaryQuery, expandedWords);
        try {
            boolean hasVector = hasDimensionEmbeddings(datasourceId);
            List<Float> queryVector = hasVector ? getQueryVector(datasourceId, primaryQuery) : List.of();
            boolean canKnn = !queryVector.isEmpty();

            if (canKnn) {
                // 混合检索：分别执行关键词查询和 kNN 查询，应用层 RRF 融合

                // 1. 关键词查询（BM25，原始关键词 must + 扩展词 should OR 拓宽召回）
                SearchResponse<Map> keywordResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );

                // 2. kNN 向量查询（只用原始关键词的向量）
                SearchResponse<Map> knnResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .knn(knn -> knn
                                        .field(DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD)
                                        .queryVector(queryVector)
                                        .k(pool)
                                        .numCandidates(Math.max(DataAgentConstants.ES_KNN_NUM_CANDIDATES, pool))
                                        .filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)))),
                        Map.class
                );

                // 3. 应用层 RRF 融合
                return rrfMergeDimensionHits(keywordResponse, knnResponse);
            } else {
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );
                return extractDimensionHits(response, "keyword", 0);
            }
        } catch (Exception e) {
            log.error("ES 维度检索失败，降级为 MySQL: {}", e.getMessage());
            List<String> allKeywords = new ArrayList<>();
            allKeywords.add(primaryQuery);
            if (expandedWords != null) {
                allKeywords.addAll(expandedWords);
            }
            return fallbackMySqlSearchDimensions(datasourceId, allKeywords, topK);
        }
    }

    /**
     * 构建维度关键词查询。逻辑同 {@link #buildMetricKeywordQuery}：原始关键词 must
     * (BestFields + tieBreaker)，扩展词 should (BestFields OR 语义)，.keyword 精确匹配
     * 改为独立 should term 提权。
     */
    private Query buildDimensionKeywordQuery(Long datasourceId, String primaryQuery, List<String> expandedWords) {
        return Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)));
            // 原始关键词 must：BestFields 取最高分字段，tieBreaker 让次高分字段也贡献部分分数
            b.must(m -> m.multiMatch(mm -> mm
                    .fields("dimName^3", "dimName.ikmax^2", "dimDisplayName^2",
                            "dimDisplayName.ikmax^1", "synonyms^2", "synonyms.ikmax^1",
                            "dimDescription^1",
                            DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^1")
                    .type(TextQueryType.BestFields)
                    .tieBreaker(0.3)
                    .query(primaryQuery)));
            // 扩展词 should：每个扩展词独立 multiMatch，OR 语义，命中任一即加分
            if (expandedWords != null) {
                for (String expanded : expandedWords) {
                    if (StringUtils.hasText(expanded) && !expanded.equals(primaryQuery)) {
                        b.should(sh -> sh.multiMatch(mm -> mm
                                .fields("dimName^2", "dimDisplayName^2", "synonyms^2",
                                        "dimDescription^1",
                                        DataAgentConstants.ALOUDATA_ES_EMBEDDING_TEXT_FIELD + "^0.5")
                                .type(TextQueryType.BestFields)
                                .tieBreaker(0.3)
                                .query(expanded)));
                    }
                }
            }
            // .keyword 精确匹配提权：命中加分，不命中不影响 must 召回
            b.should(sh -> sh.term(t -> t.field("dimName.keyword").value(primaryQuery).boost(5.0f)));
            b.should(sh -> sh.term(t -> t.field("dimDisplayName.keyword").value(primaryQuery).boost(3.0f)));
            b.should(sh -> sh.term(t -> t.field("synonyms.keyword").value(primaryQuery).boost(2.0f)));
            return b;
        }));
    }

    // ==================== 应用层 RRF 融合 ====================

    /**
     * 应用层 RRF 融合关键词和向量检索结果（指标）
     * <p>
     * RRF 公式: score = Σ 1/(k + rank_i)，k 为 rankConstant
     * 双通道同时命中的结果得分更高，自然排在前面
     */
    private List<MetricHit> rrfMergeMetricHits(SearchResponse<Map> keywordResponse, SearchResponse<Map> knnResponse) {
        int rankConstant = DataAgentConstants.SCHEMA_SEARCH_RRF_K;
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, MetricHit> hitMap = new LinkedHashMap<>();

        if (keywordResponse != null && keywordResponse.hits() != null) {
            List<Hit<Map>> hits = keywordResponse.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                Hit<Map> hit = hits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                MetricHit mh = buildMetricHit(hit.source(), hit.score(), "keyword");
                if (mh != null) {
                    hitMap.putIfAbsent(id, mh);
                }
            }
        }

        if (knnResponse != null && knnResponse.hits() != null) {
            List<Hit<Map>> hits = knnResponse.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                Hit<Map> hit = hits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                if (!hitMap.containsKey(id)) {
                    MetricHit mh = buildMetricHit(hit.source(), hit.score(), "vector");
                    if (mh != null) {
                        hitMap.put(id, mh);
                    }
                }
            }
        }

        // RRF 原始分为 Σ1/(k+rank)，量级仅 0.0x，与展示/阈值语义（0~1）不可比；
        // 按最大值归一化到 (0,1]，使 top 命中≈1.0，展示与相似度阈值可解释。
        double maxScore = scoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    MetricHit mh = hitMap.get(entry.getKey());
                    if (mh != null) {
                        mh.setScore(maxScore > 0 ? entry.getValue() / maxScore : entry.getValue());
                        mh.setMatchSource("hybrid");
                    }
                    return mh;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 应用层 RRF 融合关键词和向量检索结果（维度）
     */
    private List<DimensionHit> rrfMergeDimensionHits(SearchResponse<Map> keywordResponse, SearchResponse<Map> knnResponse) {
        int rankConstant = DataAgentConstants.SCHEMA_SEARCH_RRF_K;
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, DimensionHit> hitMap = new LinkedHashMap<>();

        if (keywordResponse != null && keywordResponse.hits() != null) {
            List<Hit<Map>> hits = keywordResponse.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                Hit<Map> hit = hits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                DimensionHit dh = buildDimensionHit(hit.source(), hit.score(), "keyword");
                if (dh != null) {
                    hitMap.putIfAbsent(id, dh);
                }
            }
        }

        if (knnResponse != null && knnResponse.hits() != null) {
            List<Hit<Map>> hits = knnResponse.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                Hit<Map> hit = hits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                if (!hitMap.containsKey(id)) {
                    DimensionHit dh = buildDimensionHit(hit.source(), hit.score(), "vector");
                    if (dh != null) {
                        hitMap.put(id, dh);
                    }
                }
            }
        }

        // 与指标同：按最大 RRF 分归一化到 (0,1]，使展示分数可解释。
        double maxScore = scoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> {
                    DimensionHit dh = hitMap.get(entry.getKey());
                    if (dh != null) {
                        dh.setScore(maxScore > 0 ? entry.getValue() / maxScore : entry.getValue());
                        dh.setMatchSource("hybrid");
                    }
                    return dh;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private MetricHit buildMetricHit(Map<String, Object> source, Double score, String matchSource) {
        if (source == null) return null;
        MetricHit mh = new MetricHit();
        mh.setMetricName(getString(source, "metricName"));
        mh.setMetricDisplayName(getString(source, "metricDisplayName"));
        mh.setType(getString(source, "type"));
        mh.setBusinessCaliber(getString(source, "businessCaliber"));
        mh.setSynonyms(getJoinedString(source, "synonyms"));
        mh.setCategoryName(getString(source, "categoryName"));
        mh.setUnit(getString(source, "unit"));
        mh.setScore(score != null ? score : 0.0);
        mh.setMatchSource(matchSource);
        return mh;
    }

    @SuppressWarnings("unchecked")
    private DimensionHit buildDimensionHit(Map<String, Object> source, Double score, String matchSource) {
        if (source == null) return null;
        DimensionHit dh = new DimensionHit();
        dh.setDimName(getString(source, "dimName"));
        dh.setDimDisplayName(getString(source, "dimDisplayName"));
        dh.setDimDescription(getString(source, "dimDescription"));
        dh.setSynonyms(getJoinedString(source, "synonyms"));
        dh.setOriginDataType(getString(source, "originDataType"));
        dh.setExampleValues(getJoinedString(source, "exampleValues"));
        dh.setScore(score != null ? score : 0.0);
        dh.setMatchSource(matchSource);
        return dh;
    }

    // ==================== ES 结果提取 ====================

    @SuppressWarnings("unchecked")
    private List<MetricHit> extractMetricHits(SearchResponse<Map> response, String matchSource, double threshold) {
        List<MetricHit> hits = new ArrayList<>();
        if (response == null || response.hits() == null) return hits;

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) continue;

            double score = hit.score() != null ? hit.score() : 0.0;
            if (threshold > 0 && score < threshold) continue;

            MetricHit mh = new MetricHit();
            mh.setMetricName(getString(source, "metricName"));
            mh.setMetricDisplayName(getString(source, "metricDisplayName"));
            mh.setType(getString(source, "type"));
            mh.setBusinessCaliber(getString(source, "businessCaliber"));
            mh.setSynonyms(getJoinedString(source, "synonyms"));
            mh.setCategoryName(getString(source, "categoryName"));
            mh.setUnit(getString(source, "unit"));
            mh.setScore(score);
            mh.setMatchSource(matchSource);
            hits.add(mh);
        }
        // 归一化 BM25 原始分到 (0,1]，与 hybrid 路径展示口径一致
        double max = hits.stream().mapToDouble(MetricHit::getScore).max().orElse(0.0);
        if (max > 0) {
            hits.forEach(h -> h.setScore(h.getScore() / max));
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private List<DimensionHit> extractDimensionHits(SearchResponse<Map> response, String matchSource, double threshold) {
        List<DimensionHit> hits = new ArrayList<>();
        if (response == null || response.hits() == null) return hits;

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) continue;

            double score = hit.score() != null ? hit.score() : 0.0;
            if (threshold > 0 && score < threshold) continue;

            DimensionHit dh = new DimensionHit();
            dh.setDimName(getString(source, "dimName"));
            dh.setDimDisplayName(getString(source, "dimDisplayName"));
            dh.setOriginDataType(getString(source, "originDataType"));
            dh.setDimDescription(getString(source, "dimDescription"));
            dh.setSynonyms(getJoinedString(source, "synonyms"));
            dh.setTimeDimension(Boolean.TRUE.equals(source.get("isTimeDimension")));
            dh.setExampleValues(getJoinedString(source, "exampleValues"));
            dh.setScore(score);
            dh.setMatchSource(matchSource);
            hits.add(dh);
        }
        // 归一化 BM25 原始分到 (0,1]，与 hybrid 路径展示口径一致
        double max = hits.stream().mapToDouble(DimensionHit::getScore).max().orElse(0.0);
        if (max > 0) {
            hits.forEach(h -> h.setScore(h.getScore() / max));
        }
        return hits;
    }

    // ==================== MySQL 降级检索 ====================

    private AloudataSearchResult fallbackMySqlSearch(Long datasourceId, List<String> keywords, int topK, long startTime) {
        AloudataSearchResult result = new AloudataSearchResult();
        String primaryQuery = keywords == null || keywords.isEmpty() ? "" : keywords.get(0);
        result.setQuery(primaryQuery);
        result.setDatasourceId(datasourceId);

        List<MetricHit> metricHits = fallbackMySqlSearchMetrics(datasourceId, keywords, topK);
        List<DimensionHit> dimensionHits = fallbackMySqlSearchDimensions(datasourceId, keywords, topK);

        enrichMetricDimensions(metricHits, datasourceId);

        result.setMetricHits(metricHits);
        result.setDimensionHits(dimensionHits);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private List<MetricHit> fallbackMySqlSearchMetrics(Long datasourceId, List<String> keywords, int topK) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AloudataMetricEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataMetricEntity::getDatasourceId, datasourceId);
        // 每个关键词独立 LIKE，OR 语义，避免合并串 LIKE 失效
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String likePattern = "%" + keywords.get(i) + "%";
                if (i == 0) {
                    w.like(AloudataMetricEntity::getMetricName, likePattern)
                     .or().like(AloudataMetricEntity::getMetricDisplayName, likePattern)
                     .or().like(AloudataMetricEntity::getBusinessCaliber, likePattern)
                     .or().like(AloudataMetricEntity::getSynonyms, likePattern);
                } else {
                    w.or().like(AloudataMetricEntity::getMetricName, likePattern)
                     .or().like(AloudataMetricEntity::getMetricDisplayName, likePattern)
                     .or().like(AloudataMetricEntity::getBusinessCaliber, likePattern)
                     .or().like(AloudataMetricEntity::getSynonyms, likePattern);
                }
            }
        });
        wrapper.last("LIMIT " + topK);

        List<AloudataMetricEntity> entities = metricMapper.selectList(wrapper);
        return entities.stream().map(e -> {
            MetricHit mh = new MetricHit();
            mh.setMetricName(e.getMetricName());
            mh.setMetricDisplayName(e.getMetricDisplayName());
            mh.setType(e.getType());
            mh.setBusinessCaliber(e.getBusinessCaliber());
            mh.setSynonyms(e.getSynonyms());
            mh.setCategoryName(e.getMetricCategoryName());
            mh.setUnit(e.getUnit());
            mh.setScore(1.0);
            mh.setMatchSource("keyword");
            return mh;
        }).collect(Collectors.toList());
    }

    private List<DimensionHit> fallbackMySqlSearchDimensions(Long datasourceId, List<String> keywords, int topK) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AloudataDimensionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataDimensionEntity::getDatasourceId, datasourceId);
        // 每个关键词独立 LIKE，OR 语义，避免合并串 LIKE 失效
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String likePattern = "%" + keywords.get(i) + "%";
                if (i == 0) {
                    w.like(AloudataDimensionEntity::getDimName, likePattern)
                     .or().like(AloudataDimensionEntity::getDimDisplayName, likePattern)
                     .or().like(AloudataDimensionEntity::getDimDescription, likePattern)
                     .or().like(AloudataDimensionEntity::getSynonyms, likePattern);
                } else {
                    w.or().like(AloudataDimensionEntity::getDimName, likePattern)
                     .or().like(AloudataDimensionEntity::getDimDisplayName, likePattern)
                     .or().like(AloudataDimensionEntity::getDimDescription, likePattern)
                     .or().like(AloudataDimensionEntity::getSynonyms, likePattern);
                }
            }
        });
        wrapper.last("LIMIT " + topK);

        List<AloudataDimensionEntity> entities = dimensionMapper.selectList(wrapper);
        return entities.stream().map(e -> {
            DimensionHit dh = new DimensionHit();
            dh.setDimName(e.getDimName());
            dh.setDimDisplayName(e.getDimDisplayName());
            dh.setOriginDataType(e.getOriginDataType());
            dh.setDimDescription(e.getDimDescription());
            dh.setSynonyms(e.getSynonyms());
            dh.setTimeDimension(Boolean.TRUE.equals(e.getIsTimeDimension()));
            dh.setExampleValues(e.getExampleValues());
            dh.setScore(1.0);
            dh.setMatchSource("keyword");
            return dh;
        }).collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 为指标命中补充可用维度列表
     */
    private void enrichMetricDimensions(List<MetricHit> metricHits, Long datasourceId) {
        if (metricHits == null || metricHits.isEmpty()) return;

        List<String> metricNames = metricHits.stream()
                .map(MetricHit::getMetricName)
                .filter(Objects::nonNull)
                .toList();
        if (metricNames.isEmpty()) return;

        LambdaQueryWrapper<AloudataMetricDimensionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataMetricDimensionEntity::getDatasourceId, datasourceId);
        wrapper.in(AloudataMetricDimensionEntity::getMetricName, metricNames);
        List<AloudataMetricDimensionEntity> relations = metricDimensionMapper.selectList(wrapper);

        Map<String, List<String>> metricDimMap = relations.stream()
                .collect(Collectors.groupingBy(
                        AloudataMetricDimensionEntity::getMetricName,
                        Collectors.mapping(AloudataMetricDimensionEntity::getDimName, Collectors.toList())
                ));

        for (MetricHit mh : metricHits) {
            mh.setAvailableDimensions(metricDimMap.getOrDefault(mh.getMetricName(), List.of()));
        }
    }

    private void ensureIndicesIfNeeded(Object entity) {
        if (indicesInitialized && cachedVectorDimension > 0) return;

        // 尝试获取向量维度
        int dim = cachedVectorDimension > 0 ? cachedVectorDimension : DataAgentConstants.DEFAULT_EMBEDDING_DIMENSION;
        ensureIndices(dim);
    }

    private boolean hasMetricEmbeddings(Long datasourceId) {
        LambdaQueryWrapper<AloudataMetricEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataMetricEntity::getDatasourceId, datasourceId);
        wrapper.isNotNull(AloudataMetricEntity::getEmbedding);
        wrapper.last("LIMIT 1");
        return metricMapper.selectCount(wrapper) > 0;
    }

    private boolean hasDimensionEmbeddings(Long datasourceId) {
        LambdaQueryWrapper<AloudataDimensionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AloudataDimensionEntity::getDatasourceId, datasourceId);
        wrapper.isNotNull(AloudataDimensionEntity::getEmbedding);
        wrapper.last("LIMIT 1");
        return dimensionMapper.selectCount(wrapper) > 0;
    }

    /**
     * 生成查询向量
     * <p>
     * 使用 EmbeddingModel 将查询文本转换为向量，用于 ES kNN 检索。
     * EmbeddingModel 不可用时返回空列表，kNN 检索将降级为关键词检索。
     */
    private List<Float> getQueryVector(Long datasourceId, String query) {
        if (embeddingModelFactory == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            EmbeddingModel embeddingModel = resolveEmbeddingModel();
            if (embeddingModel == null) {
                return List.of();
            }
            EmbeddingResponse resp = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
            float[] vector = resp.getResults().get(0).getOutput();
            if (vector == null || vector.length == 0) {
                return List.of();
            }
            List<Float> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add(v);
            }
            return result;
        } catch (Exception e) {
            log.error("生成查询向量失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析可用的 EmbeddingModel 实例
     */
    private EmbeddingModel resolveEmbeddingModel() {
        if (embeddingModelFactory == null) {
            return null;
        }
        try {
            ModelConfigEntity config = resolveEmbeddingModelConfig();
            if (config == null) {
                return null;
            }
            return embeddingModelFactory.build(config);
        } catch (Exception e) {
            log.error("构建 EmbeddingModel 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析应使用的 Embedding 模型配置
     */
    private ModelConfigEntity resolveEmbeddingModelConfig() {
        /* 优先级 1：is_default=1 的 embedding 模型 */
        ModelConfigEntity marked = modelConfigService.listEnabledModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled())
                        && "embedding".equals(m.getModelType())
                        && Boolean.TRUE.equals(m.getIsDefault()))
                .findFirst().orElse(null);
        if (marked != null) {
            return marked;
        }

        /* 优先级 2：任意 enabled 的 embedding 模型 */
        return modelConfigService.findFirstEnabledEmbedding();
    }

    private List<String> splitToList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) return List.of();
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<Float> bytesToFloatList(byte[] bytes) {
        float[] floats = WikiEmbeddingService.bytesToFloats(bytes);
        List<Float> list = new ArrayList<>(floats.length);
        for (float f : floats) {
            list.add(f);
        }
        return list;
    }

    private String getString(Map<String, Object> source, String key) {
        Object val = source.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String getJoinedString(Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val == null) return null;
        if (val instanceof List) {
            return String.join(",", ((List<String>) val).stream().filter(Objects::nonNull).toList());
        }
        return val.toString();
    }
}
