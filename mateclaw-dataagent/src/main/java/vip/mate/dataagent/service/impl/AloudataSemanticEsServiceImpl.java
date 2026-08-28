package vip.mate.dataagent.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
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
import vip.mate.dataagent.support.NameMatchSupport;
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
        List<MetricHit> metricHits = esSearchMetrics(client, datasourceId, query, List.of(), null, topK, similarityThreshold);
        List<DimensionHit> dimensionHits = esSearchDimensions(client, datasourceId, query, List.of(), null, topK, similarityThreshold);

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

        List<MetricHit> metricHits = esSearchMetrics(client, datasourceId, primaryQuery, expandedWords, null, topK, similarityThreshold);
        List<DimensionHit> dimensionHits = esSearchDimensions(client, datasourceId, primaryQuery, expandedWords, null, topK, similarityThreshold);

        // 为指标补充可用维度列表
        enrichMetricDimensions(metricHits, datasourceId);

        result.setMetricHits(metricHits);
        result.setDimensionHits(dimensionHits);
        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    @Override
    public AloudataSearchResult hybridSearchEnhanced(Long datasourceId, List<String> keywords,
                                                      String originalMessage, int topK, double similarityThreshold) {
        long startTime = System.currentTimeMillis();
        AloudataSearchResult result = new AloudataSearchResult();
        String primaryQuery = keywords == null || keywords.isEmpty() ? "" : keywords.getFirst();
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

        // originalMessage 为 null 或与首关键词相同时，退化为 hybridSearchMerged 行为
        String effectiveOriginalMessage = (originalMessage != null
                && !originalMessage.isBlank()
                && !originalMessage.equals(primaryQuery)) ? originalMessage : null;

        ElasticsearchClient client = getAvailableClient();
        if (client == null) {
            log.error("ES 不可用，降级为 MySQL LIKE 查询");
            return fallbackMySqlSearch(datasourceId, keywords, topK, startTime);
        }

        ensureIndicesIfNeeded(null);

        List<MetricHit> metricHits = esSearchMetrics(client, datasourceId, primaryQuery, expandedWords,
                effectiveOriginalMessage, topK, similarityThreshold);
        // 维度不做独立 RAG 检索：用户查询以指标为中心，维度由 enrichMetricDimensions 按指标关联确定性补充，
        // 避免维度模糊命中的假阳性干扰指标消歧与查询构造
        List<DimensionHit> dimensionHits = List.of();

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
                                            String primaryQuery, List<String> expandedWords,
                                            String originalMessage, int topK, double threshold) {
        String indexName = DataAgentConstants.ALOUDATA_METRIC_ES_INDEX;
        // 每路先放大候选池，融合/排序后再由调用方截断到 topK
        int pool = retrievalPoolSize(topK);
        Query keywordQuery = buildMetricKeywordQuery(datasourceId, primaryQuery, expandedWords);
        try {
            boolean hasVector = hasMetricEmbeddings(datasourceId);
            List<Float> queryVector = hasVector ? getQueryVector(datasourceId, primaryQuery) : List.of();
            boolean canKnn = !queryVector.isEmpty();

            if (canKnn) {
                // 混合检索：关键词查询 + 多向量 kNN，应用层 RRF 融合

                // 1. 关键词查询（BM25，should 组 + minimum_should_match=1）
                SearchResponse<Map> keywordResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );

                // 2. kNN 向量查询（原始关键词向量）
                Float knnSimilarity = knnSimilarityThreshold(threshold);
                SearchResponse<Map> knnResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .knn(knn -> buildKnnQuery(knn,
                                        DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD,
                                        queryVector, pool, knnSimilarity, datasourceId)),
                        Map.class
                );

                // 2b. 用户原话向量 kNN（如果与原始关键词不同，额外做一路 kNN 扩大向量召回）
                // 解决 LLM 压缩 keyword 后与指标展示名 embedding 空间不一致的问题：
                // 如 keyword="营收" 但展示名="营业收入"，两词 embedding 可能差异较大
                List<Hit<Map>> mergedKnnHits;
                if (originalMessage != null && !originalMessage.isBlank()
                        && !originalMessage.equals(primaryQuery)) {
                    List<Float> origMsgVector = getQueryVector(datasourceId, originalMessage);
                    if (!origMsgVector.isEmpty()) {
                        SearchResponse<Map> origMsgKnnResponse = client.search(s -> s
                                        .index(indexName)
                                        .size(pool)
                                        .knn(knn -> buildKnnQuery(knn,
                                                DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD,
                                                origMsgVector, pool, knnSimilarity, datasourceId)),
                                Map.class
                        );
                        mergedKnnHits = mergeKnnHits(List.of(knnResponse, origMsgKnnResponse));
                    } else {
                        mergedKnnHits = mergeKnnHits(List.of(knnResponse));
                    }
                } else {
                    mergedKnnHits = mergeKnnHits(List.of(knnResponse));
                }

                // 3. 应用层 RRF 融合
                return rrfMergeMetricHits(keywordResponse, mergedKnnHits, threshold);
            } else {
                // 仅关键词检索（带字段权重）
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );
                return extractMetricHits(response, "keyword", threshold);
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
     *   <li>should 组（原始关键词 + 扩展词）：multiMatch（BestFields + tieBreaker），
     *       minimum_should_match=1 保证至少命中一个 should 子句即召回</li>
     *   <li>should: .keyword 子字段精确匹配提权，命中加分、不命中不影响召回</li>
     * </ul>
     * <p>
     * 关键设计：原始关键词和扩展词统一用 should + minimum_should_match("1")，
     * 而非原始关键词用 must。must 要求原始关键词必须 BM25 命中，当 LLM 提取的 keyword
     * 为口语化表达（如"卖了多少"）时 must 完全失配，导致扩展词（如业务术语"销售额"）
     * 在关键词路上零贡献——should 子句在 must 不命中时不会参与召回。
     * <p>
     * 改为 should 组后，原始关键词或任一扩展词命中即可召回。精确匹配提权（.keyword boost）
     * 仍保留：精确匹配的文档获得更高 BM25 分数，在 RRF 排名中占据优势，确保字面精确匹配
     * 优先于扩展词模糊匹配。纯语义查询（所有 should 均未命中）时关键词路返回空，
     * kNN 路仍完整工作，RRF 退化为纯向量排序。
     */
    private Query buildMetricKeywordQuery(Long datasourceId, String primaryQuery, List<String> expandedWords) {
        return Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)));
            // 原始关键词 should：BestFields 取最高分字段，tieBreaker 让次高分字段也贡献部分分数
            b.should(m -> m.multiMatch(mm -> mm
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
            // .keyword 精确匹配提权：命中加分，不命中不影响召回
            b.should(sh -> sh.term(t -> t.field("metricName.keyword").value(primaryQuery).boost(5.0f)));
            b.should(sh -> sh.term(t -> t.field("metricDisplayName.keyword").value(primaryQuery).boost(3.0f)));
            b.should(sh -> sh.term(t -> t.field("synonyms.keyword").value(primaryQuery).boost(2.0f)));
            // 至少命中一个 should 子句才召回，避免无匹配时返回全量
            b.minimumShouldMatch("1");
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

    /**
     * 将用户语义的相似度阈值（0~1，cosine 语义）换算为 ES kNN 查询的 similarity 参数。
     * <p>
     * ES 8.x dense_vector + cosine 的文档 {@code _score = (1 + cosine) / 2}，值域 [0.5, 1.0]。
     * kNN 查询的 {@code similarity} 参数语义为"要求 _score ≥ similarity"。
     * <p>
     * 换算：{@code esSimilarity = 0.5 + userThreshold / 2}
     * <ul>
     *   <li>userThreshold=0.3 → esSimilarity=0.65 → 要求 cosine≥0.3</li>
     *   <li>userThreshold=0.5 → esSimilarity=0.75 → 要求 cosine≥0.5</li>
     *   <li>userThreshold=0   → 不设下限（返回 null 让 ES 不应用 similarity 过滤），
     *       尊重用户"放行所有"的意图；靠 RRF 后过滤剔除低质量命中</li>
     * </ul>
     *
     * @param userThreshold 用户传入的 cosine 语义阈值（0~1）
     * @return ES kNN similarity 参数值；threshold=0 时返回 null（不应用 similarity 过滤）
     */
    private Float knnSimilarityThreshold(double userThreshold) {
        if (userThreshold <= 0) {
            // threshold=0：用户有意放行所有，不在 ES 端做 similarity 过滤
            // 低质量命中靠 rrfMerge*Hits 的 threshold 后过滤在应用层剔除
            return null;
        }
        double esScore = 0.5 + userThreshold / 2.0;
        // 上限不超过 1.0
        return (float) Math.min(1.0, esScore);
    }

    /**
     * 构建 kNN 查询 builder。{@code similarity} 为 null 时不应用 similarity 过滤
     * （threshold=0 场景，用户有意放行所有，靠应用层后过滤兜底）。
     * 抽成方法是因为 ES Java Client 的 lambda 链式 builder 无法在链中做条件判断。
     */
    private KnnSearch.Builder buildKnnQuery(KnnSearch.Builder knn, String field, List<Float> queryVector,
                                            int pool, Float similarity, Long datasourceId) {
        knn.field(field)
                .queryVector(queryVector)
                .k(pool)
                .numCandidates(Math.max(DataAgentConstants.ES_KNN_NUM_CANDIDATES, pool))
                .filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)));
        if (similarity != null) {
            knn.similarity(similarity);
        }
        return knn;
    }

    /**
     * 合并多个 kNN 搜索响应，按 doc id 去重取最高分，按分数降序排列。
     * <p>
     * 用于多向量 kNN 场景：对原始关键词和用户原话分别生成向量、各自 kNN 检索后，
     * 合并为单一 hit 列表再传入 RRF 融合，避免同一文档被多次计入 RRF 排名分。
     *
     * @param knnResponses kNN 搜索响应列表（可为 null 或空）
     * @return 合并去重后的 hit 列表，按分数降序排列
     */
    private List<Hit<Map>> mergeKnnHits(List<SearchResponse<Map>> knnResponses) {
        if (knnResponses == null || knnResponses.isEmpty()) {
            return List.of();
        }
        Map<String, Hit<Map>> mergedById = new LinkedHashMap<>();
        Map<String, Double> maxScoreById = new LinkedHashMap<>();

        for (SearchResponse<Map> response : knnResponses) {
            if (response == null || response.hits() == null) {
                continue;
            }
            for (Hit<Map> hit : response.hits().hits()) {
                String id = hit.id();
                double score = hit.score() != null ? hit.score() : 0.0;
                if (!maxScoreById.containsKey(id) || score > maxScoreById.get(id)) {
                    maxScoreById.put(id, score);
                    mergedById.put(id, hit);
                }
            }
        }

        List<Hit<Map>> sorted = new ArrayList<>(mergedById.values());
        sorted.sort((a, b) -> Double.compare(
                maxScoreById.getOrDefault(b.id(), 0.0),
                maxScoreById.getOrDefault(a.id(), 0.0)
        ));
        return sorted;
    }

    /** 查询向量缓存：相同 query 短时间内重复检索时跳过 embedding API 调用，降低 P99 延迟 */
    private static final java.util.Map<String, CachedVector> QUERY_VECTOR_CACHE_TIMED = new java.util.concurrent.ConcurrentHashMap<>();
    /** 查询向量缓存 TTL（毫秒） */
    private static final long QUERY_VECTOR_CACHE_TTL_MS = 10 * 60 * 1000L;
    /** 查询向量缓存最大条数 */
    private static final int QUERY_VECTOR_CACHE_MAX_SIZE = 500;
    /** 缓存值：向量 + 写入时间戳 */
    private record CachedVector(float[] vector, long timestamp) {}

    /**
     * 生成查询向量（带缓存）。
     * <p>
     * 使用 EmbeddingModel 将查询文本转换为向量，用于 ES kNN 检索。
     * EmbeddingModel 不可用时返回空列表，kNN 检索将降级为关键词检索。
     * 相同 query 在 TTL 内复用缓存向量，避免重复调用 embedding API。
     */
    private List<Float> getQueryVector(Long datasourceId, String query) {
        if (embeddingModelFactory == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        // 缓存键：datasourceId 不影响向量（同一 embedding 模型），仅按 query 文本缓存
        String cacheKey = query;
        long now = System.currentTimeMillis();
        // 清理过期项（懒清理：仅当超过 max 时触发）
        if (QUERY_VECTOR_CACHE_TIMED.size() > QUERY_VECTOR_CACHE_MAX_SIZE) {
            QUERY_VECTOR_CACHE_TIMED.entrySet().removeIf(e ->
                    now - e.getValue().timestamp > QUERY_VECTOR_CACHE_TTL_MS);
        }
        CachedVector cached = QUERY_VECTOR_CACHE_TIMED.get(cacheKey);
        if (cached != null && (now - cached.timestamp < QUERY_VECTOR_CACHE_TTL_MS)) {
            // 缓存命中
            float[] vector = cached.vector;
            List<Float> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add(v);
            }
            return result;
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
            // 写入缓存
            QUERY_VECTOR_CACHE_TIMED.put(cacheKey, new CachedVector(vector, now));
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

    private List<DimensionHit> esSearchDimensions(ElasticsearchClient client, Long datasourceId,
                                                  String primaryQuery, List<String> expandedWords,
                                                  String originalMessage, int topK, double threshold) {
        String indexName = DataAgentConstants.ALOUDATA_DIMENSION_ES_INDEX;
        // 每路先放大候选池，融合/排序后再由调用方截断到 topK
        int pool = retrievalPoolSize(topK);
        Query keywordQuery = buildDimensionKeywordQuery(datasourceId, primaryQuery, expandedWords);
        try {
            boolean hasVector = hasDimensionEmbeddings(datasourceId);
            List<Float> queryVector = hasVector ? getQueryVector(datasourceId, primaryQuery) : List.of();
            boolean canKnn = !queryVector.isEmpty();

            if (canKnn) {
                // 混合检索：关键词查询 + 多向量 kNN，应用层 RRF 融合

                // 1. 关键词查询（BM25，should 组 + minimum_should_match=1）
                SearchResponse<Map> keywordResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );

                // 2. kNN 向量查询（原始关键词向量）
                Float knnSimilarity = knnSimilarityThreshold(threshold);
                SearchResponse<Map> knnResponse = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .knn(knn -> buildKnnQuery(knn,
                                        DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD,
                                        queryVector, pool, knnSimilarity, datasourceId)),
                        Map.class
                );

                // 2b. 用户原话向量 kNN（维度检索尤其受益：LLM 提取的 keyword 偏向指标语义，
                // 而用户原话通常包含维度上下文如"各区域""按省份"，用原话做额外 kNN
                // 可以召回 keyword 遗漏的维度，缓解指标/维度共用 keyword 的语义污染问题）
                List<Hit<Map>> mergedKnnHits;
                if (originalMessage != null && !originalMessage.isBlank()
                        && !originalMessage.equals(primaryQuery)) {
                    List<Float> origMsgVector = getQueryVector(datasourceId, originalMessage);
                    if (!origMsgVector.isEmpty()) {
                        SearchResponse<Map> origMsgKnnResponse = client.search(s -> s
                                        .index(indexName)
                                        .size(pool)
                                        .knn(knn -> buildKnnQuery(knn,
                                                DataAgentConstants.ALOUDATA_ES_EMBEDDING_FIELD,
                                                origMsgVector, pool, knnSimilarity, datasourceId)),
                                Map.class
                        );
                        mergedKnnHits = mergeKnnHits(List.of(knnResponse, origMsgKnnResponse));
                    } else {
                        mergedKnnHits = mergeKnnHits(List.of(knnResponse));
                    }
                } else {
                    mergedKnnHits = mergeKnnHits(List.of(knnResponse));
                }

                // 3. 应用层 RRF 融合
                return rrfMergeDimensionHits(keywordResponse, mergedKnnHits, threshold);
            } else {
                SearchResponse<Map> response = client.search(s -> s
                                .index(indexName)
                                .size(pool)
                                .query(keywordQuery),
                        Map.class
                );
                return extractDimensionHits(response, "keyword", threshold);
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
     * 构建维度关键词查询。逻辑同 {@link #buildMetricKeywordQuery}：原始关键词和扩展词
     * 统一用 should + minimum_should_match("1")，.keyword 精确匹配独立 should term 提权。
     */
    private Query buildDimensionKeywordQuery(Long datasourceId, String primaryQuery, List<String> expandedWords) {
        return Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("datasourceId").value(datasourceId)));
            // 原始关键词 should：BestFields 取最高分字段，tieBreaker 让次高分字段也贡献部分分数
            b.should(m -> m.multiMatch(mm -> mm
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
            // .keyword 精确匹配提权：命中加分，不命中不影响召回
            b.should(sh -> sh.term(t -> t.field("dimName.keyword").value(primaryQuery).boost(5.0f)));
            b.should(sh -> sh.term(t -> t.field("dimDisplayName.keyword").value(primaryQuery).boost(3.0f)));
            b.should(sh -> sh.term(t -> t.field("synonyms.keyword").value(primaryQuery).boost(2.0f)));
            // 至少命中一个 should 子句才召回，避免无匹配时返回全量
            b.minimumShouldMatch("1");
            return b;
        }));
    }

    // ==================== 应用层 RRF 融合 ====================

    /**
     * 加权 RRF 原始分权重。
     * <p>
     * 取值依据：RRF 排名分 rrfPart=Σ 1/(k+rank)，k=10 时值域约 [0.05, 0.18]
     * （单通道 rank1≈0.091，双通道 rank1≈0.182）。加权项 W*(bm25Norm+knnNorm) 最大为 2W，
     * 应与 rrfPart 同量级、不超过其上限，否则原始分将颠覆 RRF 的"双通道命中应优于单通道"语义。
     * <p>
     * W=0.05 时加权项上限 0.1，与 rrfPart 上限 0.18 同量级：
     * <ul>
     *   <li>双通道 rank1（两路原始分均归一化为 1.0）：0.182 + 0.1 = 0.282</li>
     *   <li>单通道 kNN rank1（kNN 原始分归一化为 1.0）：0.091 + 0.05 = 0.141</li>
     *   <li>双通道稳赢单通道，RRF 双通道提权语义得以保留</li>
     * </ul>
     * 同时 W=0.05 仍能放大纯 RRF 的微小排序差距（如 gap=0.006 可放大到 ~0.011），
     * 保留原始分对微妙场景下排序偏差的修正能力。
     * <p>
     * 不取更大的 W（如 0.3）：加权项上限 0.6 远超 rrfPart 上限 0.18，在某些 BM25 分数长尾分布下
     * （一个精确匹配 score=20，其余 score=2~3 归一化后≈0.1），低分双通道命中的加权项被压到接近 0，
     * 可能被单通道 kNN 高分命中反超，破坏双通道提权语义。
     */
    private static final double W_BM25 = 0.05;
    private static final double W_KNN = 0.05;

    /**
     * 应用层加权 RRF 融合关键词和向量检索结果（指标）。
     * <p>
     * 融合分数 = RRF 排名分 + W_BM25 * bm25归一化分 + W_KNN * knn归一化分
     * <ul>
     *   <li>RRF 排名分 = Σ 1/(k + rank)，保留原 RRF 的双通道提权语义</li>
     *   <li>bm25归一化分 = 关键词路 hit.score 归一化到 [0,1]（max 归一化），
     *       保留"精确匹配 3 词"与"勉强匹配 1 词"的区分度</li>
     *   <li>knn归一化分 = kNN 路 hit.score 归一化到 [0,1]，
     *       保留 cosine 绝对相似度信号（ES dense_vector+cosine 的 _score∈[0.5,1.0]）</li>
     * </ul>
     * 相比纯 RRF（仅用 rank），加权 RRF 让原始分高的命中排在前面，
     * 避免"完全不相关但排第一"的命中分数也被归一化到 1.0。
     * <p>
     * 融合后做 threshold 后过滤：仅出现在 kNN 路且 cosine 低于阈值的命中剔除，
     * 让 similarityThreshold 在 hybrid 路径真正生效（原实现完全忽略 threshold）。
     *
     * @param threshold 用户传入的 cosine 语义阈值（0~1），用于后过滤
     */
    private List<MetricHit> rrfMergeMetricHits(SearchResponse<Map> keywordResponse,
                                                SearchResponse<Map> knnResponse,
                                                double threshold) {
        List<Hit<Map>> knnHits = (knnResponse != null && knnResponse.hits() != null)
                ? knnResponse.hits().hits() : List.of();
        return rrfMergeMetricHits(keywordResponse, knnHits, threshold);
    }

    /**
     * 应用层加权 RRF 融合（指标），kNN 路接受预合并的 hit 列表。
     * <p>
     * 支持多向量 kNN 场景：调用方将多个 kNN 搜索响应通过 {@link #mergeKnnHits} 合并后传入。
     *
     * @param keywordResponse BM25 关键词搜索响应
     * @param knnHits         预合并的 kNN hit 列表（已按分数降序排列、去重）
     * @param threshold       用户传入的 cosine 语义阈值（0~1），用于后过滤
     */
    private List<MetricHit> rrfMergeMetricHits(SearchResponse<Map> keywordResponse,
                                                List<Hit<Map>> knnHits,
                                                double threshold) {
        int rankConstant = DataAgentConstants.SCHEMA_SEARCH_RRF_K;
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, MetricHit> hitMap = new LinkedHashMap<>();
        // 记录每个 id 在两路的原始 ES 分数，用于加权融合
        Map<String, Double> bm25ScoreMap = new LinkedHashMap<>();
        Map<String, Double> knnScoreMap = new LinkedHashMap<>();

        if (keywordResponse != null && keywordResponse.hits() != null) {
            List<Hit<Map>> hits = keywordResponse.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                Hit<Map> hit = hits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                bm25ScoreMap.put(id, hit.score() != null ? hit.score() : 0.0);
                MetricHit mh = buildMetricHit(hit.source(), hit.score(), "keyword");
                if (mh != null) {
                    hitMap.putIfAbsent(id, mh);
                }
            }
        }

        if (knnHits != null) {
            for (int i = 0; i < knnHits.size(); i++) {
                Hit<Map> hit = knnHits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                knnScoreMap.put(id, hit.score() != null ? hit.score() : 0.0);
                if (!hitMap.containsKey(id)) {
                    MetricHit mh = buildMetricHit(hit.source(), hit.score(), "vector");
                    if (mh != null) {
                        hitMap.put(id, mh);
                    }
                }
            }
        }

        // 各路原始分按 max 归一化到 [0,1]，保留绝对相似度差异
        double maxBm25 = bm25ScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double maxKnn = knnScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        // threshold 后过滤：仅出现在 kNN 路且 cosine 低于阈值的命中剔除。
        // cosine = 2 * _score - 1（ES dense_vector+cosine 的 _score=(1+cosine)/2）。
        // 出现在关键词路的命中保留（BM25 命中说明字面相关，不应因 cosine 低而剔除）。
        double cosineThreshold = threshold;
        boolean applyThreshold = cosineThreshold > 0;
        Set<String> filteredIds = new LinkedHashSet<>();
        for (String id : scoreMap.keySet()) {
            if (!applyThreshold) {
                filteredIds.add(id);
                continue;
            }
            if (bm25ScoreMap.containsKey(id)) {
                // 关键词路命中，保留
                filteredIds.add(id);
                continue;
            }
            // 仅 kNN 路：检查 cosine
            Double knnScore = knnScoreMap.get(id);
            if (knnScore == null) {
                filteredIds.add(id);
                continue;
            }
            double cosine = 2.0 * knnScore - 1.0;
            if (cosine >= cosineThreshold) {
                filteredIds.add(id);
            }
            // cosine 低于阈值且无 BM25 命中 → 剔除
        }

        // 计算加权最终分并组装结果（仅对未被后过滤剔除的 id）
        List<MetricHit> merged = filteredIds.stream()
                .map(id -> {
                    double rrfPart = scoreMap.get(id);
                    double bm25Norm = maxBm25 > 0 && bm25ScoreMap.containsKey(id)
                            ? bm25ScoreMap.get(id) / maxBm25 : 0.0;
                    double knnNorm = maxKnn > 0 && knnScoreMap.containsKey(id)
                            ? knnScoreMap.get(id) / maxKnn : 0.0;
                    double finalScore = rrfPart + W_BM25 * bm25Norm + W_KNN * knnNorm;
                    MetricHit mh = hitMap.get(id);
                    if (mh != null) {
                        mh.setScore(finalScore);
                        mh.setMatchSource("hybrid");
                    }
                    return mh;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .toList();

        // 归一化最终分到 (0,1]，使展示分数可解释、与纯关键词路径口径一致
        double maxFinal = merged.stream().mapToDouble(MetricHit::getScore).max().orElse(0.0);
        if (maxFinal > 0) {
            merged.forEach(h -> h.setScore(h.getScore() / maxFinal));
        }

        return merged;
    }

    /**
     * 应用层加权 RRF 融合关键词和向量检索结果（维度）。
     * <p>
     * 逻辑同 {@link #rrfMergeMetricHits}：RRF 排名分 + W_BM25*bm25归一化 + W_KNN*knn归一化，
     * 保留原始分区分度；融合后做 threshold 后过滤，剔除仅 kNN 路且 cosine 低于阈值的命中。
     *
     * @param threshold 用户传入的 cosine 语义阈值（0~1），用于后过滤
     */
    private List<DimensionHit> rrfMergeDimensionHits(SearchResponse<Map> keywordResponse,
                                                     SearchResponse<Map> knnResponse,
                                                     double threshold) {
        List<Hit<Map>> knnHits = (knnResponse != null && knnResponse.hits() != null)
                ? knnResponse.hits().hits() : List.of();
        return rrfMergeDimensionHits(keywordResponse, knnHits, threshold);
    }

    /**
     * 应用层加权 RRF 融合（维度），kNN 路接受预合并的 hit 列表。
     * 逻辑同 {@link #rrfMergeMetricHits(SearchResponse, List, double)}。
     */
    private List<DimensionHit> rrfMergeDimensionHits(SearchResponse<Map> keywordResponse,
                                                     List<Hit<Map>> knnHits,
                                                     double threshold) {
        int rankConstant = DataAgentConstants.SCHEMA_SEARCH_RRF_K;
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, DimensionHit> hitMap = new LinkedHashMap<>();
        Map<String, Double> bm25ScoreMap = new LinkedHashMap<>();
        Map<String, Double> knnScoreMap = new LinkedHashMap<>();

        if (keywordResponse != null && keywordResponse.hits() != null) {
            List<Hit<Map>> hits = keywordResponse.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                Hit<Map> hit = hits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                bm25ScoreMap.put(id, hit.score() != null ? hit.score() : 0.0);
                DimensionHit dh = buildDimensionHit(hit.source(), hit.score(), "keyword");
                if (dh != null) {
                    hitMap.putIfAbsent(id, dh);
                }
            }
        }

        if (knnHits != null) {
            for (int i = 0; i < knnHits.size(); i++) {
                Hit<Map> hit = knnHits.get(i);
                String id = hit.id();
                double rrfScore = 1.0 / (rankConstant + i + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                knnScoreMap.put(id, hit.score() != null ? hit.score() : 0.0);
                if (!hitMap.containsKey(id)) {
                    DimensionHit dh = buildDimensionHit(hit.source(), hit.score(), "vector");
                    if (dh != null) {
                        hitMap.put(id, dh);
                    }
                }
            }
        }

        // 各路原始分按 max 归一化到 [0,1]
        double maxBm25 = bm25ScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double maxKnn = knnScoreMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        // threshold 后过滤：仅出现在 kNN 路且 cosine 低于阈值的命中剔除
        double cosineThreshold = threshold;
        boolean applyThreshold = cosineThreshold > 0;
        Set<String> filteredIds = new LinkedHashSet<>();
        for (String id : scoreMap.keySet()) {
            if (!applyThreshold) {
                filteredIds.add(id);
                continue;
            }
            if (bm25ScoreMap.containsKey(id)) {
                filteredIds.add(id);
                continue;
            }
            Double knnScore = knnScoreMap.get(id);
            if (knnScore == null) {
                filteredIds.add(id);
                continue;
            }
            double cosine = 2.0 * knnScore - 1.0;
            if (cosine >= cosineThreshold) {
                filteredIds.add(id);
            }
        }

        // 计算加权最终分并组装结果
        List<DimensionHit> merged = filteredIds.stream()
                .map(id -> {
                    double rrfPart = scoreMap.get(id);
                    double bm25Norm = maxBm25 > 0 && bm25ScoreMap.containsKey(id)
                            ? bm25ScoreMap.get(id) / maxBm25 : 0.0;
                    double knnNorm = maxKnn > 0 && knnScoreMap.containsKey(id)
                            ? knnScoreMap.get(id) / maxKnn : 0.0;
                    double finalScore = rrfPart + W_BM25 * bm25Norm + W_KNN * knnNorm;
                    DimensionHit dh = hitMap.get(id);
                    if (dh != null) {
                        dh.setScore(finalScore);
                        dh.setMatchSource("hybrid");
                    }
                    return dh;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .toList();

        // 归一化最终分到 (0,1]
        double maxFinal = merged.stream().mapToDouble(DimensionHit::getScore).max().orElse(0.0);
        if (maxFinal > 0) {
            merged.forEach(h -> h.setScore(h.getScore() / maxFinal));
        }

        return merged;
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
        // 相关性阈值闸门：归一化后再过滤，与 hybrid 路径阈值语义一致
        // （BM25 原始分量级 5~30，归一化前过滤形同虚设）
        if (threshold > 0) {
            hits = hits.stream()
                    .filter(h -> h.getScore() >= threshold)
                    .collect(Collectors.toList());
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
        // 相关性阈值闸门：归一化后再过滤，与 hybrid 路径阈值语义一致
        // （BM25 原始分量级 5~30，归一化前过滤形同虚设）
        if (threshold > 0) {
            hits = hits.stream()
                    .filter(h -> h.getScore() >= threshold)
                    .collect(Collectors.toList());
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
        // 每个关键词独立 LIKE，OR 语义，避免合并串 LIKE 失效；
        // 核心字段（metricName/展示名）额外加"拆段 % 连接"的标点不敏感模式（如「客户 - 收入」→「%客户%收入%」），
        // 覆盖 LLM 格式化（加空格/改标点/全角半角）导致的字面 LIKE 失配
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String likePattern = "%" + keywords.get(i) + "%";
                String segPattern = NameMatchSupport.likePattern(keywords.get(i));
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
                if (segPattern != null) {
                    w.or().like(AloudataMetricEntity::getMetricName, segPattern)
                     .or().like(AloudataMetricEntity::getMetricDisplayName, segPattern);
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
        // 每个关键词独立 LIKE，OR 语义，避免合并串 LIKE 失效；
        // 核心字段（dimName/展示名）额外加"拆段 % 连接"的标点不敏感模式（如「客户 - 收入」→「%客户%收入%」），
        // 覆盖 LLM 格式化（加空格/改标点/全角半角）导致的字面 LIKE 失配
        wrapper.and(w -> {
            for (int i = 0; i < keywords.size(); i++) {
                String likePattern = "%" + keywords.get(i) + "%";
                String segPattern = NameMatchSupport.likePattern(keywords.get(i));
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
                if (segPattern != null) {
                    w.or().like(AloudataDimensionEntity::getDimName, segPattern)
                     .or().like(AloudataDimensionEntity::getDimDisplayName, segPattern);
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
